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

## 11. Owner Feel Revision (2026-09-29)

The owner reviewed 0.26.1 and judged the swings "not like swinging a sword". Capture showed the first-person strike keys (`0.56-0.60`) landing after the server active window, every segment easing to a stop, the blade presented edge-on at screen center, and no hit, sound, or trail feedback. The owner approved hiding the first-person arm for now while requiring a complete arm later.

- [x] 11.1 Replace hard-coded normalized first-person curves with the tick-authored shoulder-pivot rig in `assets/myvillage/combat/qingfeng_first_person.json`, loaded by a client reload listener; validate server move parity, neutral endpoints, and strike windows that cover each active window within three ticks.
- [x] 11.2 Undo the vanilla handheld display offset so the rig grip is the Qingfeng handle, mirror the left hand, and tune all five swings in a `960x540`, FOV-70 capture using the `/myvillage_pal_smoke first_person <move> <tick>` probe and resource reload.
- [x] 11.3 Withdraw the segmented skin/sleeve arm layer and its tests/validator checks; keep no first-person arm until 11.8.
- [x] 11.4 Add presentation-only feedback: per-move swing/hit cues, `CombatSounds` events aliased through `sounds.json` with bilingual subtitles, server swing sound excluding the attacker, local swing sound on the visual timeline, post-damage hit sound/particles, and the attacker-only `CombatHitConfirmPayload` (protocol `5`).
- [x] 11.5 Add the client hit-stop clock with catch-up to the server total, the first-person 剑光 ribbon re-posed from the rig, and the world-space ribbon from hitbox samples using the broadcast facing yaw.
- [x] 11.6 Extend Java tests, focused validator checks, negative fixtures, docs, and apply the small-feature version rule to `0.26.2`.
- [x] 11.7 Record the owner's verdict on the revised swings, trails, sounds, and hit-stop. Recorded 2026-09-30: after watching A next to Epic Fight the owner said A "现在不太行看上去" and asked for an optimized A: "我要的是那种战斗真实动作游戏的感觉". Verdict: the revised swings were NOT accepted; section 12 is the response.
- [x] 11.8 Superseded: 11.7 did not accept the sword motion, and the lead pulled the complete first-person arm forward into 12.7. Its acceptance is tracked in section 12.

## 12. Action-feel revision (2026-09-30)

Owner direction after the 11.7 verdict: "我要的是那种战斗真实动作游戏的感觉". Totals, active windows, multipliers, target caps, ranges, server authority, and the empty C2S payloads are unchanged. Java items below are covered by the combat JUnit suite (108 tests passing in the lead's build) and the focused validator.

- [x] 12.1 Add per-move `bufferStartTick` (the active start) and `chainTick` (`7/8/10/13/20`) to `AttackMoveDefinition` with invariants `activeStartTick <= bufferStartTick < totalTicks` and `activeEndTick < chainTick <= totalTicks`. A held click cancels recovery into the next move through stop-then-start. Move five cannot chain. Keep the one-slot buffer, and do not count a rejected click toward the minimum interval.
- [x] 12.2 Face the view yaw at each move start, snap body and head to it, add the removable `myvillage:combat_commit` movement modifier, stop sprinting at start (server and predicting client), and move the server swing sound to `activeStartTick - 1`.
- [x] 12.3 Replace the server-side fifth-move `player.move` with server-decided step impulses on every move (`StepDefinition` bound `(0, 1.6]`, move five `1.40`). Use `setDeltaMovement` + `hurtMarked` with `GROUND_DRAG_COMPENSATION`, keep the collision/support search, add ±30° magnetism with a `0.6` standoff, and resolve hits from the server-planned origin.
- [x] 12.4 Add `ReactionDefinition` and `CombatReactionService`: mob freeze for the hit-stop with a held slide, hitstun AI stall and melee suppression, stun falloff, boss exemption, never freezing players (slide plus `myvillage:combat_stun` slow instead), vanilla hurt knockback replaced for our hits only, invulnerability restored to the larger timer, and cleanup on death, unload, dimension change, and server start/stop.
- [x] 12.5 Add the presentation-only `CombatImpactPayload` (clientbound only; ids, revision, move index, and contact points; no damage or health), sent to the attacker and trackers. Wire `CombatAttackReceiver` with 4 consumers and bump the payload protocol to `6`.
- [x] 12.6 Add per-move hit-stop (`SwingClock.beginHitStop`, anchored at the rig contact tick) and the re-authored first-person rig (cubic/overshoot easing, contact ticks, 2-tick chain cross-fade). Add `CombatCameraFx` (shake, kicks, FOV punch scaled by the accessibility options, plus the combat-slow FOV correction) and `CombatImpactFx` (client target freeze/jitter, remote attacker hit-stop).
- [x] 12.7 Add the complete first-person arm: `QingfengFirstPersonArmRenderer`, `QingfengFirstPersonArmIk` (two-bone, hand on the grip), and `QingfengFirstPersonArmModel`, registered before the trail and never cancelling `RenderHandEvent`.
- [x] 12.8 Replace the additive trail with `SWORD_TRAIL_TRANSLUCENT` thin tapered ribbons, remove the vanilla sweep particle, cut crit sparks, add the `myvillage:blade_cut` particle (generated original sprite, `tools/gen_blade_cut_sprite.py`), add the `combat.sword.impact_heavy` sound with bilingual subtitles, and add swing pitch variation with a local whoosh that leads by about one tick.
- [x] 12.9 Generate `sword_combat.json` from `tools/gen_sword_pal_anims.py` (planted feet, hitbox-matched cut directions, lunge on the step tick, `--check` drift and self-checks). Add chain cross-fade, graceful stop, and `setHitStopRate` to `CombatAnimationController`.
- [x] 12.10 Update the focused validator and its negative fixtures for protocol `6`, chain windows and invariants, all five steps, impulse steps, swing-sound timing, the S2C-only impact payload without authority fields, no player freeze, the FOV correction, translucent trails, no sweep particle, the heavy-impact sound, blade-cut resources, arm registration and no-cancel, and both generator `--check` drift checks.
- [x] 12.11 Update the specs (buffer from active start, chain windows, view-yaw facing, impulse steps and bound, shipped cut directions, target reaction, impact payload, camera effects, first-person arm, generated poses), KB 32, README, AGENTS.md, and CHANGELOG.
- [x] 12.12 Apply the large-feature version rule to `0.27.0` in `gradle.properties`, `neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md`.
- [ ] 12.13 Rebuild and inspect the `myvillage-0.27.0.jar` (focused validator jar checks, no shaded PAL), rerun `./gradlew test`/`build`, and run a bounded acceptance server.
- [x] 12.14 Record the lab station E physical-client capture (2026-09-30, `/home/ubuntu/code/mc/combat-lab/out/E`) in the README ledger: first-person arm holding the sword, thin trails, blade_cut particles, target slide, move-five lunge displacement, camera roll on heavy hits, and third-person full-body poses. This is implementation evidence only.
- [ ] 12.15 Real-client verification of the remaining `not_verified` 0.27.0 surfaces:
  - chain timing under real clicks;
  - step distances, magnetism, and wall/cliff suppression under the impulse;
  - per-move hit-stop, hitstun, player-target slow, and boss exemption;
  - sounds and subtitles;
  - no slowness FOV zoom and accessibility scaling;
  - chained prediction and remote impact effects in two-client multiplayer.
- [x] 12.16 Owner verdict on the 0.27.0 action-feel revision, quoted without inference (2026-09-30, after the lab E capture): "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。" Resolved by section 13.

## 13. Grip and 3D sword model revision (2026-09-30)

Owner feedback on 0.27.0: "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。" Presentation only: server authority, timing, hit windows, damage, steps, and the empty C2S payloads are unchanged.

- [x] 13.1 Replace the extruded Qingfeng sprite in hand with a generated 3D jian model (`tools/gen_qingfeng_sword_model.py`, `--check` drift): blade with ridge and straight tapered tip, guard, wrapped grip, pommel. Keep the existing 2D sprite as the GUI icon through `neoforge:separate_transforms`. Evidence: generator `--check` passes; lab F `selftest/vanilla_mode_fp_3d.png` shows the 2D hotbar icon, the 3D sword in hand, and no missing textures; `grip_v1_extra/` covers the item frame and dropped item.
- [x] 13.2 Publish the sword geometry contract `assets/myvillage/combat/qingfeng_sword_geometry.json` (grip centre, handle, guard, pommel, blade base/tip, axes) from the same generator, and set third-person display transforms so the handle crosses the fist with the guard above it and the PAL `right_item` design still holds (`tools/gen_sword_pal_anims.py --check`). Evidence: both generators' `--check` pass, including the new per-key `right_item` grip compensation self-checks; lab F `grip_v1_tp/` and `compare_E_vs_F/compare_tp_*.png` show the handle through the fist without drifting out during moves.
- [x] 13.3 Rework the first-person arm into upper arm, forearm, wrist, and a separate fist oriented by the grip frame; the handle crosses the fist with the guard and pommel visible, the IK solves to the wrist, and wrist bend stays within anatomical limits for every rig key and sampled tick. Evidence: `FirstPersonArmIkTest` (wrist limits, fist around the handle, blade clearance) passes in `./gradlew build`; lab F `grip_v1/fp_grip.png` shows the fist on the handle with the guard visible.
- [x] 13.4 Add presentation-only wrist secondary motion (lag on acceleration, follow-through on stops) and shrink the arm's on-screen footprint. Evidence: `FirstPersonArmLag`; `cutsTrailTheArmThenFollowThrough` passes; the arm cross-section is set by `rig.arm.thickness`.
- [x] 13.5 Align the first-person sword from the geometry contract and the baked first-person display transform instead of hard-coded sprite offsets; derive first-person trail blade points from the contract and match the world-trail blade length. Evidence: `GRIP_*` constants removed and rejected by the validator; `swordGripLandsOnTheGripFrameWhateverTheDisplayTransform`, `SwordGeometryTest`, and `CombatWorldTrailsTest` pass.
- [x] 13.6 Update the focused validator, mod-item validator, and their tests for the new model structure, geometry contract, and arm registration. Evidence: both validators and their unit tests pass.
- [x] 13.7 Update specs, KB 32, README, AGENTS.md, and CHANGELOG; apply the small-feature version rule to `0.27.1`. Evidence: `gradle.properties`, `neoforge.mods.toml`, README jar names, and `CHANGELOG.md` moved to `0.27.1`; `player-animation-integration`, `sword-combat-foundation`, `resource-export`, `validation`, and `design.md` updated; `openspec validate add-sword-combat-foundation --strict` passes.
- [x] 13.8 Rebuild, rerun `./gradlew test`/`build`, and record lab station F grip close-ups (first person per move, third person back/front) against 0.27.0 as implementation evidence. Done 2026-10-01: `myvillage-0.27.1.jar` rebuilt after the last grip tuning (`./gradlew build`, 273 tests, 0 failures; both validators and all three generator `--check` runs pass). Lab F captures: `grip_v3/` (43 first-person stills, reactive-dummy first-person and F5 clips, g1 80 → 44.18 HP), `grip_v3_tp/`, `compare_E_vs_F_v3/` (0.27.0 vs 0.27.1 per move and view), `grip_v4/` (wrist-cap recheck), `grip_v1_extra/` (item frame, dropped item, ridge close-up). Implementation evidence only.
- [x] 13.9 Owner verdict on the grip and sword model, quoted without inference (2026-10-01, after viewing the 0.27.0 vs 0.27.1 comparison page): "感觉271看上去可以"; then "暂时就这样". No further change was requested; commit/merge and the real-client checks in the README ledger remain open.
