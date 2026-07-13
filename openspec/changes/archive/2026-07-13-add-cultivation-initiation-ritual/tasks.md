## 1. Contract And Current-API Baseline

- [x] 1.1 Strictly validate this change's proposal, design, and all delta specs before runtime edits, and record the owner-directed exception that combines awakening plus inheritance while preserving them as independent rituals.
- [x] 1.2 Re-read the current cultivation foundation Java/tests/resources and confirm actual Minecraft `1.21.1` / NeoForge `21.1.233` mappings for block interaction, Overworld seed access, `DeferredRegister.Blocks`, current registry lookup, attachment replacement, payload direction, and server/client event signatures.
- [x] 1.3 Record the implementation boundary: reuse the v1 profile, `CultivationService`, datapack registries, clientbound snapshot, read-only H screen, and current administrator commands; add no Capability, `SavedData`, `SimpleChannel`, client-authored state, BlockEntity, or profile schema field.

## 2. Spiritual-Element Weights And Technique Requirements

- [x] 2.1 Extend `SpiritualElementDefinition` and its codec with optional/defaulted `awakening_weight` (`1` by default, legal `0..1_000_000`) while preserving all old datapack JSON that omits the field.
- [x] 2.2 Update the five shipped spiritual-element JSON definitions to declare `awakening_weight: 1` explicitly without hard-coding those ids into runtime awakening.
- [x] 2.3 Add codec tests for omitted/default, zero, maximum, negative, and over-maximum awakening weights plus complete record round trips.
- [x] 2.4 Implement a pure reusable `TechniqueRequirementEvaluator` that resolves current realm/stage ordering and element-affinity requirements from `TechniqueDefinition.requirements`, fails closed on missing/ambiguous references, and does not mutate profiles.
- [x] 2.5 Update shipped `myvillage:basic_breathing` to require minimum realm `myvillage:mortal` and minimum stage `myvillage:mortal_qi_sensed`, with no element/affinity restriction and no executor.
- [x] 2.6 Extend definition/reference tests so all valid root counts pass basic-breathing element eligibility, lower realm/stage and insufficient affinity fail, and evaluator behavior comes from the current definition rather than a hard-coded stage condition.

## 3. Pure Deterministic Spiritual-Root Generator

- [x] 3.1 Add immutable generator input/result records and `SpiritualRootGenerator` under the cultivation root package with no `ServerPlayer`, level, position, time, client, or external random dependency.
- [x] 3.2 Implement the fixed algorithm constants (`ALGORITHM_VERSION = 1`, `ROOT_AWAKENING_SALT = 0x4D5956494C4C4147L`), UTF-8 FNV-1a id folding, local SplitMix64-style state/mixer, and rejection-sampled bounded-long draws without `String.hashCode` as the seed algorithm or Minecraft/JDK PRNG behavior.
- [x] 3.3 Canonicalize positive-weight candidates by full id, reject duplicates, exclude weight `0`, and use checked `long` accumulation/cumulative selection so empty sets and overflow return controlled errors.
- [x] 3.4 Implement the fixed root-count distribution `10/25/35/20/10`, clamp to eligible count, cap at five, and perform awakening-weighted selection without replacement.
- [x] 3.5 Implement exact integer affinity apportionment: single element `10000`; otherwise base `1000` each, positive stable weights, checked integer floor shares, largest-remainder correction, id-order ties, positive selected values only, and exact sum `10000`.
- [x] 3.6 Add pinned golden vectors and generator tests for same-input equality, candidate-order independence, multiple UUIDs, all one-through-five count buckets, zero-weight exclusion, no replacement, count clamp, empty candidates, maximum weights/overflow handling, stable ties, positive/no-zero affinities, exact totals, and absence of time/position/current-dimension dependence.

## 4. Atomic Spiritual-Root Awakening

- [x] 4.1 Implement `SpiritualRootAwakeningService` with structured `SUCCESS`, `ALREADY_AWAKENED`, `INVALID_PROFILE_STATE`, `NO_ELIGIBLE_ELEMENTS`, `GENERATION_FAILED`, and `UPDATE_REJECTED` results.
- [x] 4.2 Make the service read the profile through `CultivationService`, reject every existing root, accept only rootless mortal `mortal_unawakened` or administrator-cleared `mortal_qi_sensed`, and never force a rootless non-mortal/other-stage profile back to mortal.
- [x] 4.3 Read only the server Overworld seed, build the sorted current registry candidate set, call the pure generator, and ensure the seed is never persisted, snapshotted, chatted, or written to ordinary logs.
- [x] 4.4 Construct one immutable replacement that sets root plus `mortal_qi_sensed` while preserving schema `1`, realm, progress, stability, power, and techniques, then commit once through `CultivationService` so success emits one final snapshot and every failure emits none.
- [x] 4.5 Add service tests for default success, every preserved field, one atomic replacement seam invocation, repeat no-reroll, root installed by `setroot`, cleared sensed-mortal repair, rootless non-mortal rejection, empty/failed generation, rejected update, current-registry membership, and reset replay under unchanged deterministic inputs; keep real attachment/snapshot delivery in the manual evidence ledger.

## 5. Definition-Driven Basic-Technique Inheritance

- [x] 5.1 Implement `TechniqueInheritanceService` for fixed id `myvillage:basic_breathing` with structured `SUCCESS`, `NOT_AWAKENED`, `REQUIREMENTS_NOT_MET`, `TECHNIQUE_NOT_REGISTERED`, `ALREADY_LEARNED`, and `UPDATE_REJECTED` results.
- [x] 5.2 Enforce result precedence: resolve the current definition first; if present, return `ALREADY_LEARNED` before later root/stage checks so existing mastery is never reset or misreported; only a not-yet-learned profile proceeds to root and shared requirements evaluation.
- [x] 5.3 On success, add exactly `basic_breathing -> TechniqueProgress.ZERO` in one immutable `CultivationService` replacement and preserve schema, realm, stage, root, progress, stability, power, other techniques, and existing mastery values.
- [x] 5.4 Keep `learn` as the separate administrator bypass tool, and ensure normal inheritance never equips/executes the technique or grants cultivation, spiritual power, stability, realm/stage changes, mastery growth, attributes, or effects.
- [x] 5.5 Add inheritance tests for unawakened, wrong realm/stage, successful mastery `0`, every preserved field, one atomic replacement seam invocation, repeat with nonzero mastery, already-learned precedence after later root/stage change, missing definition with saved unknown id, requirements failure, rejected update, and failure without a committer invocation; keep real attachment/snapshot delivery in the manual evidence ledger.

## 6. Testing Stele And Inheritance Stele

- [x] 6.1 Add separate `SpiritTestingSteleBlock` and `TechniqueInheritanceSteleBlock` registrations through current `DeferredRegister.Blocks` APIs, matching BlockItems, creative-tab entries, and both ids in `ModBlocks.verifyRegistered`.
- [x] 6.2 Implement current vanilla block interaction so only the logical server invokes the corresponding service once, main/off-hand processing cannot double-submit, and full vanilla sound/particle effects occur only after a committed `SUCCESS`.
- [x] 6.3 Add translatable result messages for every awakening and inheritance status; format awakening affinities descending by value then id, fall back to raw ids for missing labels, and state after inheritance that H shows the technique but this version cannot execute it.
- [x] 6.4 Add original/vanilla-material blockstates, block models, item models, loot tables, appropriate mineable tool-tag entries, `en_us`/`zh_cn` block names and interaction messages for both ids under `src/main/resources`.
- [x] 6.5 Verify both blocks deliberately have no BlockEntity, menu, recipe, natural generation, structure template, structure-export integration, or block-local player data and remain obtainable only through creative inventory or `/give`.
- [x] 6.6 Extend static block/item/resource validation for distinct registrations, matching BlockItems, creative/verify coverage, complete resources, mining/drop data, no BlockEntity, logical-server service routing, and sided-success handling; keep physical main/off-hand dispatch and mining/drop behavior in the manual evidence ledger.

## 7. Commands, Snapshot, And Read-Only Client Regression

- [x] 7.1 Add `awaken` and `juexing` under both `/myvillage cultivation` and `/myvillage xiulian`, with self execution and one standard optional target path, one shared awakening handler, permission level `2`, and no seed/element/affinity/count/reroll/force arguments.
- [x] 7.2 Add `initiate` and `rumen` under both roots, with self execution and one standard optional target path, one shared inheritance handler for fixed `basic_breathing`, and no technique-id or eligibility-bypass argument.
- [x] 7.3 Preserve all existing English/pinyin command literals and low-level administrator semantics, and add command-tree tests for both roots, every new alias, optional-target shape, forbidden descendants, and old-command regression; require shared service routing through focused source validation and keep live repeat behavior in the manual evidence ledger.
- [x] 7.4 Reuse the existing clientbound `CultivationSnapshotPayload` and v1 profile shape without adding any cultivation play-to-server payload; verify the single-commit/no-commit source contract and leave actual client snapshot delivery for successful, failed, and repeated actions in the manual evidence ledger.
- [x] 7.5 Verify the H screen renders the three expected phases from the read-only snapshot and add no awaken/learn/reroll/equip/execute/meditation control, while preserving client-side isolation and the existing sharp-content/background-blur ordering.
- [x] 7.6 Preserve the existing flying-sword payload flags, wire format, serverbound registration, and handler checks and retain dedicated-server protection from client-only H-screen classloading.

## 8. Focused Validator And Automated Test Coverage

- [x] 8.1 Add standard-library-only `tools/validate_cultivation_initiation.py` covering OpenSpec/KB presence, both block/BlockItem registrations, creative/verify coverage, absence of BlockEntities, complete resources/translations/tool tags, explicit shipped weights, fixed generator salt/prohibited inputs, both services/evaluator, service routing, mutation authority, payload direction, schema `1`, basic-breathing requirements/no executor, read-only H screen, docs/release sync, and jar packaging.
- [x] 8.2 Add `tools/tests/test_validate_cultivation_initiation.py` positive shipped-tree coverage and negative fixtures for missing resources/registrations/translations, illegal weights, hard-coded or nondeterministic generator dependencies, direct attachment writes, wrong service routing, new cultivation C2S payload, wrong basic requirements/executor, schema/UI regression, and stale docs/version/jar examples.
- [x] 8.3 Keep deterministic selection/affinity math, service state transitions, one-committer-invocation behavior, failure without a committer invocation, repeat no-reroll, and repeat no-mastery-reset in Java tests; make the Python validator require those test surfaces without pretending source-string checks or the injected seam prove actual attachment/snapshot delivery.
- [x] 8.4 Run the core cultivation validator/tests during development so the new field, definition requirements, snapshot, persistence, existing commands, and foundation behavior remain backward-compatible.

## 9. Documentation, Command Manual, And Feature Version

- [x] 9.1 Add `docs/ai-kb/29_cultivation_initiation_ritual.md` with deterministic inputs/salt/version, registry sorting/weight semantics, count distribution, exact affinity method, atomic awakening, reset/datapack behavior, evaluator/inheritance order, two stele ids, command aliases, authority boundary, validation/manual evidence, and non-goals; cross-link the capability specs.
- [x] 9.2 Update `docs/ai-kb/INDEX.md` and `docs/ai-kb/28_cultivation_core.md` so the learning chain and shipped-scope text acknowledge the owner-directed two-step exception and no longer claim awakening, initiation commands, or cultivation blocks are wholly absent.
- [x] 9.3 Update README command/usage and acceptance sections with both `/give` commands, all English/pinyin awaken/initiate routes, deterministic input and repeat semantics, H-screen read-only behavior, creative/`/give`-only acquisition, and the remaining absence of meditation, recovery, cultivation gain, advancement, and technique execution.
- [x] 9.4 Update AGENTS and directly related validation/command guidance together where acceptance preparation changes, referencing the authoritative version rule rather than duplicating it.
- [x] 9.5 Apply the feature version rule from `openspec/config.yaml` atomically by updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.
- [x] 9.6 Re-run release-sensitive build, jar-content, documentation, and server-smoke checks after the version update and confirm no generated reports, run worlds, logs, caches, or structure-export outputs are staged.

## 10. Final Automated Verification And Server Smoke

- [x] 10.1 Run and record `openspec validate add-cultivation-initiation-ritual --type change --strict` and `openspec validate --specs --strict`; any nonzero result blocks closeout.
- [x] 10.2 Run and record `python3 tools/validate_cultivation_core.py`, `python3 tools/validate_cultivation_initiation.py`, `python3 -m unittest tools.tests.test_validate_cultivation_core`, `python3 -m unittest tools.tests.test_validate_cultivation_initiation`, and applicable `python3 tools/validate_mod_items.py` checks.
- [x] 10.3 Run and record `./gradlew test` and `./gradlew build`, then inspect the produced jar for both stele classes, blockstates, block/item models, loot tables, translations, tool tag, updated element JSONs, and updated `basic_breathing` JSON.
- [x] 10.4 Run and cleanly stop `python3 tools/run_chunky_acceptance.py --stage 1` or the current bounded dedicated-server smoke, and inspect logs for registry freeze/datapack/element codec/technique codec, duplicate or wrong-direction payload, client-only classloading, missing block/item/model/loot/translation, cultivation snapshot, and flying-sword payload regressions.
- [x] 10.5 Record the server smoke only as lifecycle/registration/side-safety evidence and do not call it visual, block-interaction, H-screen, persistence, or gameplay acceptance.

## 11. Manual Acceptance Ledger And CRAFT Closeout

- [x] 11.1 Record `pass`, `fail`, or `not_verified` for the default H profile, one-shot testing-stele awakening, displayed `mortal_qi_sensed` root totaling `10000`, absence of automatic technique learning, repeat no-reroll/full-effect, separate inheritance-stele learning at mastery `0`, and repeat no-mastery-reset.
- [x] 11.2 Record `pass`, `fail`, or `not_verified` for relog, save/restart, true death, dimension change, `reset`, exact basis-point deterministic reawakening, and successful re-inheritance; never infer these lifecycle results from startup/build success.
- [x] 11.3 Record `pass`, `fail`, or `not_verified` for all four awaken/juexing routes and all four initiate/rumen routes, equivalent behavior, repeat invariants, and preservation of every existing cultivation command.
- [x] 11.4 Record `pass`, `fail`, or `not_verified` for both stele drops/mining and creative-tab presence, H-screen clarity/blur regression, flying-sword regression, inability to execute basic breathing, and absence of automatic cultivation progress, spiritual power, or qi-refining advancement.
- [x] 11.5 Inspect the final diff against every explicit non-goal and confirm it contains only initiation implementation/resources/tests/docs/release metadata plus CRAFT/OpenSpec evidence, with no run world, generated report, cache, log, unrelated refactor, profile v2, C2S cultivation payload, worldgen, sect, region, meditation, recovery, progression, or execution work.
- [x] 11.6 Complete CRAFT front-door checks and task evidence; archive/synchronize the change only after required automated gates are green and all manual items carry truthful verdicts, then follow the repository branch, commit, fast-forward merge, push, and final clean-status workflow.
