## 1. Governance And Contracts

- [x] 1.1 Record the current item, client-input, payload, attachment, lifecycle, cultivation-conflict, side-isolation, and active-change map as CRAFT evidence.
- [x] 1.2 Write `docs/ai-kb/32_pal_combat_integration.md` with the exact PAL filename, SHA-256, metadata, mod id, packages, API, resource format/path, client entry, first-person boundary, dedicated-server requirement, license/distribution conclusion, local Gradle route, verified commands, and damage-route decision; add it to `docs/ai-kb/INDEX.md` with spec links.
- [x] 1.3 Classify `myvillage:qingfeng_sword` as a functional item, write `genops/contracts/items/qingfeng_sword.json`, validate it against the Item Contract schema, and record the visual verdict as pending.
- [x] 1.4 Materialize CRAFT/OpenSpec and mod-item run evidence with explicit self-executed role ownership or authorized worker results before each protected file family is edited.
- [x] 1.5 Strictly validate the complete proposal, design, six delta specs, and this task graph before runtime implementation.

## 2. Gate A PAL Integration

- [x] 2.1 Add exact root-jar Gradle resolution and a clear missing-file `GradleException`; keep the jar unshaded, unpacked, copied, and uncommitted.
- [x] 2.2 Declare the inspected required `player_animation_library` dependency and compatible version range on `BOTH` in NeoForge metadata.
- [x] 2.3 Add the physical-client PAL layer registration at priority 1600 through `FMLClientSetupEvent#enqueueWork`, plus isolated play, elapsed-tick correction, transition, stop, and pose-reset adapters.
- [x] 2.4 Add an original `sword_mode_enter` smoke animation at `assets/myvillage/player_animations/` and a bounded client smoke entry that can trigger and stop it in a world.
- [x] 2.5 Add focused PAL identity, dependency, import-boundary, resource-format, controller-registration, and no-shading validation with negative fixtures.
- [x] 2.6 Prove the exact jar compiles and the PAL resource/controller APIs resolve with the current Minecraft, NeoForge, mappings, and Java versions.
- [x] 2.7 Start a physical client, enter a world, directly observe smoke play, stop, transition, and normal-pose restoration, and retain logs/evidence.
- [x] 2.8 Start and cleanly stop the bounded dedicated acceptance server with PAL present; prove no PAL/MyVillage client-only classloading or mixin error.
- [x] 2.9 Record Gate A as pass only when every required compile/client/play-stop/server item is evidenced; otherwise stop with the exact error, classes/methods, reproduction, and repair options.

## 3. Qingfeng Sword Item Slice

- [x] 3.1 Register `myvillage:qingfeng_sword` with mapped diamond tier/attributes and expose it beside the independent rideable sword in `myvillage:main`.
- [x] 3.2 Add `en_us`/`zh_cn` item names and messages, ordinary item model, shaped recipe, and vanilla sword-tag membership.
- [x] 3.3 Create and land an original transparent pixel texture with a narrow Chinese double-edged blade, small guard, dark wrap, and cyan-jade accent; record dimensions and visual evidence without self-accepting it.
- [x] 3.4 Extend mod-item and focused validation for registration, exact mapped attributes, creative tab, lang, model, texture, recipe, tag, and jar packaging.

## 4. Preference Input And Payloads

- [x] 4.1 Implement `CombatMode`, immutable `CombatPreference`, codecs, `CombatAttachments.PREFERENCE`, copy-on-death persistence, and replacement-only `CombatService` ownership outside `CultivationProfile`.
- [x] 4.2 Synchronize authoritative preference on login, respawn, dimension change, and accepted toggle; maintain a read-only client cache and clear it on disconnect.
- [x] 4.3 Add empty C2S mode/attack intents plus revisioned S2C mode/start/stop payloads through the existing registrar and advance the protocol once without changing existing payload contracts.
- [x] 4.4 Add configurable default-`R` combat mode `KeyMapping`, bounded click consumption, translatable action-bar feedback, and no client-selected mode.
- [x] 4.5 Intercept only cancelable mapped attack actions for a live no-GUI cultivation-mode Qingfeng user, suppress vanilla swing/mining, debounce sends, and preserve every unsupported item/vanilla-mode action.
- [x] 4.6 Add codec/payload/input/rate-policy tests, including proof that the client cannot submit combo index, targets, damage, hitboxes, movement, or completion.

## 5. Definitions And Pure Session State

- [x] 5.1 Implement immutable `CombatStyleDefinition`, `AttackMoveDefinition`, `HitboxDefinition`, and `AnimationDefinition` types plus one `BasicSwordStyle` owner.
- [x] 5.2 Encode all five ids, display keys, total/active ticks, multipliers, target caps, ranges, animation ids, sample families, knockback, buffer windows, timeout, and bounded fifth-step data in that owner.
- [x] 5.3 Implement a pure `CombatSession` transition machine for move selection, server-tick timing, one-slot late buffer, early-input rejection, timeout, fifth reset, miss continuation, revision, hit deduplication, and stop reasons.
- [x] 5.4 Implement `CombatSessionManager` with UUID sessions, packet-rate policy, independent unfinished-action recovery locks, weapon/world checks, server ticks, and lifecycle cleanup.
- [x] 5.5 Route sword intent and meditation/advancement start through idempotent mutual interruption without merging attachments or transient state.
- [x] 5.6 Add deterministic unit tests for codecs/defaults, all combo transitions, buffer capacity, early/rate rejection, timeout/fifth reset, weapon/mode interruption, recovery exploits, cultivation exclusion, and hit deduplication.

## 6. Gate B First-Move Vertical Slice

- [x] 6.1 Complete the original 11-tick `basic_sword_01_thrust` full-body animation and validate its id, length, bones, active-frame alignment, and recovery.
- [x] 6.2 Implement broad-phase AABB plus narrow center-thrust OBB/capsule samples with bounded tolerance, wall clip, legal target filtering, deterministic ordering, one target, and one-hit deduplication.
- [x] 6.3 Implement `CombatDamageService` using NeoForge attack gating, current attack attribute, move multiplier, current item bonus, enchantment damage/knockback/post-attack helpers, ordinary player-attack `hurt`, and once-per-successful-action sword durability; exclude vanilla duplicate/sweep/critical/sprint behavior.
- [x] 6.4 Tick accepted server actions through active frames and completion, then broadcast revisioned start/stop to the attacker and tracking players.
- [x] 6.5 Play/correct/stop local prediction and authoritative local/remote PAL animation without adding PAL imports to common code.
- [x] 6.6 Add first-move geometry, target legality, wall, damage event/enchantment/durability, active-window, broadcast revision, and cleanup tests.
- [x] 6.7 Exercise Qingfeng -> R -> intercepted attack intent -> authoritative first move -> PAL animation -> active hit -> real damage -> remote animation -> clean end, and record Gate B only from complete evidence.

## 7. Gate C Complete Five-Move Style

- [x] 7.1 Author and land `sword_ready_idle` plus moves two through five as original connected full-body animations with exact contract lengths and a visible fifth-step pose.
- [x] 7.2 Implement right-to-left horizontal move-two samples, left-low/right-high rising move-three samples, right-high/left-low thicker move-four samples, and long thrust move-five samples without aliasing move one.
- [x] 7.3 Implement deterministic multi-target caps, constrained horizontal/vertical tolerance, ordinary light knockback, same-world/PvP/team/invulnerability filtering, and wall blocking across all active ticks.
- [x] 7.4 Implement the at-most-0.8-block server-owned move-five step with path clip, player collision, cliff-support check, normal movement, and actual start-to-end swept hit volume.
- [x] 7.5 Add permission-gated transient `/myvillage combat debug on|off` particles, default off, without changing hit authority.
- [x] 7.6 Complete ready-idle/enter/attack transitions, one-buffer chain handoff, timeout/fifth reset, all stop reasons, nearby-player synchronization, and cleanup on death/logout/dimension/item/mode/mount/cultivation conflicts.
- [x] 7.7 Add distinct-shape boundary tests, maximum-target tests, move-five swept/collision/cliff tests, five-animation parity tests, remote revision tests, and full combo/lifecycle regression tests.
- [x] 7.8 Exercise all five moves without placeholders and record Gate C only when definitions, animations, geometry, step, damage, synchronization, reset, and interruption evidence is complete.

## 8. Focused Validation Documentation And Visual Evidence

- [x] 8.1 Finish `tools/validate_sword_combat_foundation.py` and `tools/tests/test_validate_sword_combat_foundation.py` with named negative results for dependency, authority, side, item, definition, animation, geometry, damage, docs, and jar drift.
- [x] 8.2 Keep `tools/validate_mod_items.py` complete for the new functional sword and run its tests without weakening existing item checks.
- [x] 8.3 Update README acquisition/controls/debug guidance and the full ordered real-client ledger for vanilla/cultivation, five moves, ranges, wall/dedup/step, first person, multiplayer, lifecycle, persistence, and regressions.
- [x] 8.4 Update `docs/ai-kb/09_validation_checklist.md`, `AGENTS.md`, and relevant baseline acceptance probes together for the new focused validator and PAL/combat gate sequence.
- [x] 8.5 Update the PAL/combat KB note with final API use, damage-route evidence, timing/hitbox tuning, first-person conclusion, side/server results, and unresolved limits.
- [x] 8.6 Generate and inspect texture/animation evidence, record blocking visual defects/fix rules, and leave the human verdict pending until the owner accepts, rejects, or accepts with changes.

## 9. Aggregate Build And Runtime Evidence

- [x] 9.1 Run strict validation for this change, affected baseline specs, all specs, CRAFT pipelines/front-door provenance, and Item Contract schema.
- [x] 9.2 Run the focused combat validator/tests, `tools/validate_mod_items.py`, `tools/validate_rideable_flying_sword.py`, all seven cultivation validators, GuideME validation, and the full validator test suite.
- [x] 9.3 Run `./gradlew test` and retain the complete result.
- [x] 9.4 Run `./gradlew build`; inspect the current-version jar for all Qingfeng/combat/PAL-animation resources and no shaded PAL content.
- [x] 9.5 Run and cleanly stop `./gradlew runAcceptanceServer`; inspect logs for PAL dependency, registry, attachment, payload, codec, duplicate-handler, and client-only classloading failures.
- [x] 9.6 Run the client/PAL smoke against the final artifact and record startup, resource reload, controller, play/stop, and first-person observations separately.
- [x] 9.7 Execute the documented real-client and two-player checklist; mark each item only `pass`, `fail`, or `not_verified` and fix every observed blocker before closeout.
- [x] 9.8 Reproduce the owner-reported first-person no-response defect, reject the clipping `THIRD_PERSON_MODEL` probe, add packet-free local hand/sword feedback, and cover the boundary with focused negative validation.
- [x] 9.9 Keep third-person PAL playback and add a separate client-only five-move Qingfeng first-person held-item layer; validate registration, move/timing parity, authoritative correction, bounded transforms, recovery, and a physical-client first-person smoke.
- [x] 9.10 Apply the owner's first-person revision with an explicit `1.20` amplitude factor and equal-duration retiming that starts earlier, peaks later, and recovers later; extend tests and focused negative validation, run a physical-client five-move silhouette/clipping smoke, and refresh preview evidence.
- [x] 9.11 Add the independent local skin-arm/sleeve rendering foundation, isolated wide/slim models, shared item timeline, eligibility/no-cancel/no-authority boundaries, and physical-client evidence; retain the owner's rejection that the first separately damped transform did not keep the hand on the handle.
- [x] 9.12 Replace the rejected arm transform with a shared full parent frame, neutral fallback, calibrated three-dimensional wrist pivot, and reverse-order inverse rotation that cannot move the grip contact; cover registration/transform order/pivot invariants with negative and Java tests, rerun the five-move physical-client smoke, and replace the preview/manual evidence without inferring acceptance.
- [x] 9.13 Supersede fixed-factor-only first-person amplification with per-move viewport-envelope calibration; under a `960x540`, `16:9`, FOV-70 reference capture, prove each move spans at least `0.50` on one screen axis, enters the central band, and does not remain wholly in the lower-right quadrant while preserving normalized keyframe ranges, server timing/authority, the shared parent frame, and the wrist grip; extend tests, focused validation, preview, and physical-client evidence without inferring owner acceptance.
- [x] 9.14 Replace the rejected complete-cuboid first-person arm with a MyVillage-owned segmented skin/sleeve shoulder-driver, forearm, hand, and screen-edge elbow connector; author five shoulder/elbow/wrist tracks, preserve the distal grip through forward kinematics and grip-anchored scale, support wide/slim and right/left arms, extend tests and focused validation, and capture all five physical-client moves without adding Epic Fight/GeckoLib code, assets, dependencies, or authority.

## 10. Release Verdict And Closeout

- [x] 10.1 Apply the large-feature version rule atomically by updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` from the current line to `0.26.0`.
- [x] 10.2 Re-run release-sensitive strict validation, focused/aggregate validators, Gradle tests/build, jar inspection, acceptance server, and final client smoke after the version update.
- [ ] 10.3 Record the owner's explicit Qingfeng texture/animation/gameplay verdict and resolve every required change without inferring acceptance from automation.
- [ ] 10.4 Complete the requirement-by-requirement evidence audit, sync delta specs, archive the change through CRAFT, fast-forward the finished branch to `main`, push, and report final branch/worktree state while leaving the uncommitted third-party jars untouched.
