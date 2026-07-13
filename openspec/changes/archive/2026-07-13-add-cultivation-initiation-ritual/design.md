## Context

The archived `add-cultivation-core-foundation` change established three synced datapack registries, immutable version-1 `CultivationProfile` values, a codec-backed `copyOnDeath` player attachment, `CultivationService` as the only mutation boundary, one owning-client `CultivationSnapshotPayload`, a read-only `H` profile screen, and permission-level-2 administrator commands. The shipped `myvillage:basic_breathing` entry is metadata only, and the existing profile already has all state this change needs: optional `spiritualRoot`, `realmId`, `stageId`, and `learnedTechniques`.

This change is the next narrow gameplay slice. It adds two separate but sequential initiation actions: deterministic spiritual-root awakening, then definition-gated inheritance of `myvillage:basic_breathing`. The logical server remains authoritative. Neither a block, a command, nor the client may write the attachment directly, and the existing snapshot is sufficient because the profile shape does not change.

The target remains Minecraft `1.21.1`, NeoForge `21.1.233`, and Java `21`. Block interaction, `DeferredRegister.Blocks`, registry access, payload registration, and dedicated-server side isolation must therefore use the mappings and method signatures actually present in this checkout rather than older Forge APIs.

## Goals / Non-Goals

**Goals:**

- Produce a stable `SpiritualRoot` from the Overworld seed, player UUID, fixed algorithm constants, and the current eligible spiritual-element ids and weights.
- Keep generation independent of registry iteration order, current dimension, position, time, weather, biome, player/client randomness, and Minecraft's internal PRNG implementation.
- Add `awakening_weight` without breaking spiritual-element datapacks that omit it.
- Select one through five distinct eligible elements by the fixed `10/25/35/20/10` count distribution and normalize positive integer affinities to exactly `10000` basis points.
- Atomically install root plus `myvillage:mortal_qi_sensed` once through `CultivationService`.
- Evaluate `TechniqueDefinition.requirements` through a reusable evaluator and teach `myvillage:basic_breathing` at mastery `0` without changing unrelated profile state.
- Expose the two actions through separate steles and equivalent English/pinyin administrator command routes.
- Reuse the existing snapshot and read-only `H` screen and preserve dedicated-server side safety.
- Provide algorithmic unit tests, focused validation, packaged-resource checks, server smoke, documentation, release synchronization, and truthful manual evidence.

**Non-Goals:**

- No meditation/state machine, technique executor, spiritual-power cap or recovery, cultivation gain, efficiency calculation, mastery growth, qi-refining advancement, breakthroughs, stability growth, equipment slots, skill hotbar, combat art, element damage/matchups, or root-derived numeric advantage.
- No root quality, tier, rarity, heavenly/true/false-root classification, washing, reroll, force-awaken, player-selected seed/element/affinity/count, persistent generator version, awakening timestamp, or new profile field.
- No automatic learning during awakening and no interpretation of fewer elements as stronger or more valuable.
- No BlockEntity, menu, recipe, natural stele generation, sect/worldgen integration, `RegionProfile.qi` integration, NPC, quest, alchemy, crafting system, or flying-sword protocol/rule change.
- No cultivation play-to-server mutation payload, client calculation of root/eligibility, mutation control in the `H` screen, profile schema v2, or migration of already saved roots.

## Decisions

### 1. One new capability owns a two-step ritual; existing baselines receive deltas

`cultivation-initiation-ritual` owns the cohesive player workflow and its two server services. The actions remain separate in code, registry ids, commands, messages, and acceptance because they will later belong to different sect facilities. Existing profile, registry, lifecycle, synchronization, command, validation, resource, and documentation requirements receive delta specs rather than being restated as a parallel foundation.

This is an explicit owner-directed scope exception to the guidance in `docs/ai-kb/28_cultivation_core.md` that the next cultivation change choose only one foundation boundary. The owner has required two consecutive initiation steps in one change. The exception does not collapse them into one action: awakening and inheritance retain distinct services, blocks, registry ids, commands, failure states, tests, and future facility integration boundaries. It authorizes only these two initiation steps and does not authorize the later basic-breathing execution or meditation boundary.

This avoids splitting a single minimal onboarding flow into two changes that could temporarily leave an awakened player with no rules-based entry technique. It also avoids putting generator rules inside the broad profile capability or facility rules inside the definition-registry capability.

Alternative rejected: one state-dependent block that awakens on first use and teaches on second use. It obscures authorization and acceptance boundaries and prevents later placement in separate halls.

### 2. `SpiritualElementDefinition` gains one backward-compatible field

The definition record and codec add:

```text
awakening_weight: int, optional in JSON, default 1, range 0..1_000_000
```

Weight `0` excludes an element from ordinary awakening; a positive value makes it eligible. The five shipped elements explicitly write `awakening_weight: 1`, but no Java code treats those five ids as the complete element set. Existing third-party datapacks load unchanged because omission means `1`. Codec construction and Python validation reject negative or greater-than-maximum values.

Candidate construction reads the current `myvillage:spiritual_element` registry, keeps only positive weights, and canonicalizes by the complete `ResourceLocation#toString` value. Duplicate ids are rejected at the generator input boundary even though a live registry normally prevents them. Total weights and cumulative weights use `long` plus checked addition; overflow produces a controlled generation failure rather than wraparound.

Alternative rejected: a hard-coded five-element table. It contradicts the existing generic root model and would make datapack elements invisible to awakening.

### 3. The generator is a pure, versioned deterministic function

`SpiritualRootGenerator` accepts only an Overworld seed, a UUID, and immutable candidate `(id, awakeningWeight)` records. It does not accept `ServerPlayer`, `ServerLevel`, `BlockPos`, current tick/time, dimension, biome, weather, moon phase, a client object, or an externally mutable/random source.

The algorithm fixes these public implementation constants:

```text
ALGORITHM_VERSION = 1                 // code constant only, never persisted
ROOT_AWAKENING_SALT = 0x4D5956494C4C4147L
SPLITMIX_GAMMA = 0x9E3779B97F4A7C15L
```

`ROOT_AWAKENING_SALT` is the ASCII-derived `MYVILLAG` domain separator. The generator canonicalizes positive-weight candidates by full id, folds the Overworld seed, UUID most/least-significant bits, algorithm version, and every sorted candidate id/weight into one state, then produces draws with a locally implemented SplitMix64-style state advance and fixed 64-bit mixer. Resource-location strings are folded as UTF-8 bytes with a fixed FNV-1a-64 pass before the SplitMix mixer; Java `String.hashCode` is not the seed algorithm.

The bounded-long draw uses rejection sampling over non-negative 63-bit output so weighted choices are unbiased and do not depend on `RandomSource` or JDK `Random` behavior. Every arithmetic overflow in candidate totals or distribution intermediates is checked and returned as a controlled generator error. Golden-vector tests pin the exact algorithm, including salt, byte encoding, mixing, bounded draws, selected ids, and affinity vector.

The determinism promise is therefore scoped correctly:

```text
same Overworld seed
+ same UUID
+ same positive-weight element id/weight set
+ same generator algorithm version
= same SpiritualRoot
```

It is intentionally not a claim that seed plus UUID stays equal across datapack or algorithm changes. The algorithm version is not a profile field and is not sent to the client.

Alternatives rejected:

- Minecraft/JDK random classes are not stable enough to be the serialized algorithm contract.
- Current time, tick, player random, position, dimension, or biome would make reset/retest nondeterministic.
- Raw registry iteration order would make pack insertion order observable.
- Persisting the seed or an awakening-version field would expand schema v1 unnecessarily and leak world-seed-derived material.

### 4. Count selection and weighted element selection are separately fixed

The first bounded draw is in `[0, 100)` and maps to the requested root size:

| Draw | Rolled count | Weight |
|---|---:|---:|
| `0..9` | 1 | 10 |
| `10..34` | 2 | 25 |
| `35..69` | 3 | 35 |
| `70..89` | 4 | 20 |
| `90..99` | 5 | 10 |

`effectiveCount = min(rolledCount, eligibleElementCount)`, so one eligible element always yields one affinity and more than five eligible elements never yields more than five. An empty eligible list returns a controlled no-candidates result.

Each element slot performs weighted selection without replacement: draw in `[0, totalRemainingWeight)`, walk the remaining id-sorted candidates with checked cumulative `long` addition, choose the containing interval, remove that candidate, and recompute the checked total. A selected id cannot appear twice.

Root count is a result difference only. No runtime code maps one, two, three, four, or five elements to quality, power, progress, damage, stage, technique eligibility, or rewards.

### 5. Affinities use integer largest-remainder apportionment

For one selected element, the generator assigns `10000`. For `count > 1`, it gives every selected id a base `1000`, leaving `10000 - count * 1000`. It then draws one stable positive apportionment weight in `1..1_000_000` per selected id.

For each id:

```text
product = remainingBasisPoints * apportionmentWeight
floorShare = product / totalApportionmentWeight
remainder = product % totalApportionmentWeight
```

All intermediates use checked `long` arithmetic. After floor shares are added, the unassigned basis points are awarded by remainder descending and then full `ResourceLocation` string ascending. The final immutable map is stored in id order and is asserted to contain only selected ids, positive values, and an exact sum of `10000`.

This method avoids floating point, iteration-order leakage, negative/zero selected affinities, and the biased fallback of placing all rounding error on an arbitrary final map entry.

### 6. Awakening is one service transaction

`SpiritualRootAwakeningService` owns the runtime workflow and returns a structured result such as:

```text
SUCCESS
ALREADY_AWAKENED
INVALID_PROFILE_STATE
NO_ELIGIBLE_ELEMENTS
GENERATION_FAILED
UPDATE_REJECTED
```

The service:

1. Reads the current profile through `CultivationService`.
2. Returns `ALREADY_AWAKENED` if `spiritualRoot` is present, including roots installed by `setroot`.
3. Accepts only realm `myvillage:mortal` with stage `myvillage:mortal_unawakened` or `myvillage:mortal_qi_sensed`; the latter permits ordinary reawakening after administrator `clearroot`. A rootless non-mortal or another mortal stage is rejected without rewriting realm/stage.
4. Reads `server.overworld().getSeed()` even when the interaction occurs in another dimension. The seed is never logged, chatted, snapshotted, or persisted.
5. Builds the current canonical candidate list and invokes the pure generator.
6. Constructs one immutable replacement that preserves schema version, realm, progress, stability, current spiritual power, and all learned techniques while setting the generated root and stage `myvillage:mortal_qi_sensed`.
7. Calls `CultivationService.updateProfile` or an equivalently validating extension once. One successful replacement calls attachment `setData` once and sends one final snapshot; every failure commits and sends nothing.

Reset clears the profile to its normal default. A later awaken call repeats the same affinity vector only when Overworld seed, UUID, eligible id/weight set, and algorithm version remain equal.

Alternative rejected: `setSpiritualRoot` followed by `setRealmAndStage`; it exposes an invalid intermediate state and sends two snapshots.

### 7. Datapack changes affect only future generation

Existing `SpiritualRoot` values remain raw id-to-affinity maps and are never recalculated on login, registry reload, datapack change, or mod update. Unknown saved element ids remain decodable, preserved, synchronized, and displayed by raw id under the foundation contract.

Newly awakened players use the current positive-weight set. An administrator `reset` followed by awakening may therefore produce a different root after element ids, weights, eligible membership, or the algorithm version changes. There is no automatic migration and no removal of unknown ids.

This is why the UI and documentation must not state that world seed plus UUID alone guarantees cross-datapack permanence.

### 8. Technique requirements become executable through one evaluator

`TechniqueRequirementEvaluator` consumes a current immutable profile, one `TechniqueDefinition`, and current realm/element registries. It returns a structured satisfied/unsatisfied/unavailable result rather than changing the profile.

- Minimum realm compares the current and required `RealmDefinition.sortOrder`. A lower order fails; a higher order satisfies the realm/stage floor. Equal order with a different realm id is treated as ambiguous/unsatisfied rather than guessed.
- When current and minimum realm ids match and a minimum stage exists, both stages are resolved in that realm and compared by `RealmStageDefinition.stageOrder`. A missing/mismatched stage is unsatisfied/unavailable.
- Every `minimum_element_affinity` entry requires a present root and an actual value greater than or equal to the declared basis points. An empty map imposes no element restriction.
- `TechniqueDefinition.elements` remains descriptive metadata and is not silently treated as an eligibility list.
- Missing referenced definitions fail closed and do not alter the profile.

The shipped `myvillage:basic_breathing` definition sets minimum realm `myvillage:mortal`, minimum stage `myvillage:mortal_qi_sensed`, and no element-affinity requirements. Every valid one-to-five-element awakened root is therefore eligible at that stage; no hard-coded `if stage == mortal_qi_sensed` is the sole gate.

Alternative rejected: embedding realm/stage checks in the inheritance block/service. That would duplicate definition semantics and make later manuals or jade slips disagree.

### 9. Inheritance is a separate service transaction

`TechniqueInheritanceService` owns the normal-rules path for the fixed `myvillage:basic_breathing` id and returns a structured result such as:

```text
SUCCESS
NOT_AWAKENED
REQUIREMENTS_NOT_MET
TECHNIQUE_NOT_REGISTERED
ALREADY_LEARNED
UPDATE_REJECTED
```

It resolves the definition first, preserving a saved unknown technique id but returning `TECHNIQUE_NOT_REGISTERED` when the datapack removed `basic_breathing`. When the definition exists, it next checks whether the profile already learned the id and returns `ALREADY_LEARNED` before inspecting later root/stage changes, so an existing mastery can never be reset or misreported as a new eligibility failure. Only a not-yet-learned profile proceeds to the present-root check and current-definition evaluator. Success constructs one replacement whose only change is adding `myvillage:basic_breathing -> TechniqueProgress.ZERO`, commits through `CultivationService` once, and sends only the final snapshot.

All realm, stage, root, progress, stability, spiritual power, other learned techniques, schema version, and existing mastery values remain equal. Learning does not equip or execute the technique and grants no power, progress, stability, realm, stage, attribute, or effect change.

The existing permissioned `learn` command remains an administrator low-level mutation route that can bypass normal initiation eligibility after resolving a registered definition. `initiate`/`rumen` never call that bypass handler.

### 10. Two simple blocks expose the services without owning state

`SpiritTestingSteleBlock` and `TechniqueInheritanceSteleBlock` use the current Minecraft `1.21.1` vanilla block-interaction override and server-side branch. They return the appropriate sided interaction result so the off hand cannot submit the same use after the main hand handled it. The client neither predicts success nor sends custom cultivation data.

Each block delegates exactly once to its service. Only `SUCCESS` plays the complete vanilla sound/particle feedback and relies on the service's successful snapshot. Failure/repeat states display controlled translatable messages and do not play the full success effect. Awakening success formats affinities by value descending then id ascending; missing definition translation falls back to the raw id and never crashes.

The blocks have independent ids:

```text
myvillage:spirit_testing_stele
myvillage:technique_inheritance_stele
```

Each has a matching `BlockItem`, `myvillage:main` creative-tab entry, `ModBlocks.verifyRegistered` coverage, blockstate, block model, item model, loot table, mineable tool tag, `en_us`/`zh_cn` name and messages, and jar inclusion. They use simple original/vanilla-material presentation. They have no BlockEntity, menu, recipe, worldgen placement, or block-local player data and are obtained through creative inventory or `/give`.

### 11. Commands mirror the block services and keep alias symmetry

Both command roots expose both English and pinyin literals:

```text
/myvillage cultivation awaken [target]
/myvillage cultivation juexing [target]
/myvillage xiulian awaken [target]
/myvillage xiulian juexing [target]

/myvillage cultivation initiate [target]
/myvillage cultivation rumen [target]
/myvillage xiulian initiate [target]
/myvillage xiulian rumen [target]
```

Without `target`, the executing player is used; with `target`, the existing standard single-player argument applies. All inherit permission level `2`. Every awakening route shares one handler that calls `SpiritualRootAwakeningService`, and every inheritance route shares a different handler that calls `TechniqueInheritanceService`. No route accepts seed, element, affinity, count, or technique id, and none provides reroll/force behavior.

The existing `info`, `reset`, scalar, `setroot`, `clearroot`, `learn`, `forget`, and `setmastery` routes and their pinyin aliases remain unchanged. Command-tree tests compare roots, aliases, optional target descendants, and handler/service boundaries.

### 12. Existing snapshot and UI are sufficient

The successful services mutate only existing v1 fields. `CultivationSnapshotPayload` and its `StreamCodec` therefore remain shape-compatible and clientbound-only. `ClientCultivationState` remains a read-only latest-snapshot cache, and `CultivationProfileScreen` continues to render unawakened/awakened roots and learned techniques without buttons or mutation APIs.

No `AwakenPayload`, root-generation payload, inheritance payload, or other cultivation play-to-server payload is added. Common/server ritual classes import no client-only types. Dedicated-server validation explicitly checks that the H-screen classes are not loaded and that the existing flying-sword serverbound protocol remains unchanged.

### 13. Validation separates algorithm proof from source/resource integration

Java tests, not source-text matching, prove:

- golden vectors, identical-input determinism, order independence, all five count outcomes, candidate clamping, positive-weight eligibility, no replacement, exact affinity sum/positivity, stable ties, empty/overflow failure, and codec weight bounds/default;
- one-commit awakening, allowed/invalid states, exact preserved fields, repeated-call no-reroll, reset replay under unchanged inputs, current-registry membership, and no snapshot on failure;
- definition-driven realm/stage/affinity evaluation and element-neutral `basic_breathing` eligibility;
- one-commit inheritance, mastery exactly `0`, preserved fields/other techniques, repeat no-reset, missing definition, unknown saved ids, and no snapshot on failure;
- all command aliases, optional targets, service routing, forbidden arguments, main/offhand duplicate prevention where testable, and existing command regression.

`tools/validate_cultivation_initiation.py` plus `tools.tests.test_validate_cultivation_initiation` deterministically checks capability/KB presence, Java/resource registrations, two distinct blocks/BlockItems, creative/verify coverage, lack of BlockEntities/recipes/worldgen claims, resource and bilingual translation completeness, all five explicit weights, generator/service/evaluator integration, fixed salt and prohibited input/random APIs, mutation boundary, payload direction, v1 schema, `basic_breathing` requirements/no executor, read-only UI, docs/release synchronization, and jar-name examples. It does not claim to prove the generator math; Java tests own that evidence.

Closeout gates run targeted strict change validation, all baseline specs strict validation, both cultivation validators and their Python tests, mod-item validation where applicable, `./gradlew test`, `./gradlew build`, jar-content inspection, and a bounded stage-1 dedicated/acceptance-server smoke. The captured log is checked for registry freeze/datapack codec, payload direction/duplication, client-only classloading, missing registry/resource, snapshot, and flying-sword regressions.

### 14. Manual acceptance remains an observed verdict ledger

The manual checklist covers the default H screen, one-shot stele awakening, exact displayed affinity total, repeat no-reroll/effect, separate stele inheritance, repeat no-mastery-reset, persistence across relog/server restart/death/dimension, reset plus deterministic reawakening, every English/pinyin command path, resource/drop/creative-tab behavior, H-screen sharpness, existing command/flying-sword regression, and continued absence of execution/progress/power/advancement.

Every item records `pass`, `fail`, or `not_verified`. A green build, server startup, or headless Chunky stage is not visual or interaction proof. The stage-1 acceptance server proves bounded startup/registration only.

### 15. Documentation and release updates stay synchronized

The change adds `docs/ai-kb/29_cultivation_initiation_ritual.md`, indexes and cross-links it, updates the foundation note's exclusions/next-change boundary, and updates README/AGENTS/spec command and acceptance guidance. README explains the deterministic input set including datapack weights, repeat semantics, separate stele acquisition, read-only H screen, and the remaining absence of meditation, recovery, progress gain, advancement, and technique execution.

The feature version is chosen during implementation from the authoritative rule in `openspec/config.yaml`. One release task updates `gradle.properties`, `neoforge.mods.toml`, README jar examples, and `CHANGELOG.md` together, then re-runs release-sensitive build, jar, and server checks. No version number is hard-coded in this design.

## Risks / Trade-offs

- [Changing the eligible datapack set changes future roots] -> Scope determinism to the sorted positive-weight id/weight set, preserve existing roots, and document reset semantics.
- [A generator refactor silently changes results] -> Pin salt/mixer/bounded-draw/count/apportionment behavior with golden vectors and increment the code-only algorithm version for intentional changes.
- [Weighted totals or apportionment arithmetic overflows] -> Bound per-definition weight, use checked `long` addition/multiplication, and return controlled failure before profile mutation.
- [Realm ordering is ambiguous for custom datapacks] -> Fail closed on equal-order different realms or missing stage membership; validate shipped references and document evaluator ordering.
- [An administrator clears root but leaves `mortal_qi_sensed`] -> Explicitly permit this narrow repair state for reawakening while rejecting rootless non-mortal/other-stage profiles.
- [A removed `basic_breathing` definition coexists with saved progress] -> Preserve the profile entry, fail initiation as `TECHNIQUE_NOT_REGISTERED`, and never delete or reset mastery.
- [Block interaction fires through both hands] -> Use the current 1.21.1 sided interaction result contract and add focused regression coverage.
- [Success effects happen before commit] -> Services return structured results; blocks/commands emit success-only feedback after the successful atomic replacement.
- [A source validator overclaims algorithm correctness] -> Keep numeric/state proofs in Java tests and limit Python validation to wiring, resources, forbidden APIs, docs, and packaging.
- [Custom block assets are mechanically present but visually poor] -> Record asset inspection separately and keep unobserved Minecraft appearance `not_verified`.
- [The change grows into meditation/progression] -> Enforce explicit non-goals in capability specs, validator checks, final diff review, and the next-change boundary.

## Migration Plan

1. Add the optional/defaulted `awakening_weight` codec field, update five shipped element files, and update `basic_breathing` requirements; validate old-field omission compatibility before gameplay code.
2. Implement and golden-test the pure generator and shared requirements evaluator without server/player dependencies.
3. Add the awakening and inheritance services using one existing `CultivationService` replacement per success and no new profile fields.
4. Register the two blocks/BlockItems and complete resources, then connect server-side interactions and translatable feedback.
5. Add all command aliases through shared service handlers and preserve existing administrator routes.
6. Add the dedicated validator/tests and documentation, apply the synchronized feature version task, then run strict specs, cultivation/mod-item validation, Gradle tests/build, jar inspection, and bounded server smoke.
7. Record every actually observed manual item as `pass`/`fail` and leave all others `not_verified`; only then perform CRAFT closeout/archive according to repository governance.

Existing saves require no migration because schema version stays `1`. Existing roots and learned techniques are not recalculated. Rollback before players use the ritual removes the new blocks/services/resources and restores the definition JSON/codec while retaining the defaulted-field read compatibility as needed. After use, rollback must continue decoding the unchanged v1 root and learned-technique fields; it must not delete those profile values merely because the interaction surface is absent.

## Open Questions

No owner decision blocks implementation. Exact class package placement, current 1.21.1 block-interaction override, vanilla sound/particle choices, and original stele asset details may follow the checked-out code and mappings, but the ids, deterministic inputs/constants, count distribution, integer affinity contract, atomic service boundaries, alias set, profile/network non-changes, release rule, and non-goals are fixed.
