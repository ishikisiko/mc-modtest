## Context

The current repository targets Minecraft 1.21.1, NeoForge 21.1.233, and Java 21. Item registration is centralized in `ModItems`; `myvillage:main` is the existing creative tab. `ModPayloads` owns one payload registrar at protocol `3`, the flying-sword client sends only a bounded input bitset, and cultivation synchronization uses clientbound immutable snapshots plus input-only meditation intents. `CultivationAttachments.PROFILE` is persistent and copy-on-death, while meditation and advancement sessions are server-memory-only. Client classes live under `com.example.myvillage.client` and client subscribers are physical-side isolated.

The supplied root jar is `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`, SHA-256 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`. Its NeoForge metadata declares mod id `player_animation_library`, version `1.1.4+mc.1.21.1`, required NeoForge `[21.1,)`, required Minecraft `[1.21.1,)`, side `BOTH`, and MIT License. Its public packages are `com.zigythebird.playeranim` and `com.zigythebird.playeranimcore`. The embedded MIT text permits use and redistribution when its notice is retained, but the repository has no established third-party-binary commit policy, so this change does not commit, shade, unpack, or copy the jar.

Local `jar tf`/`javap` inspection and the exact official `1.21.1` branch establish the API used by this design:

- NeoForge controller factories must register during `FMLClientSetupEvent#enqueueWork` through `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(layerId, priority, factory)`.
- Each client player receives a `PlayerAnimationController`; `PlayerAnimationAccess.getPlayerAnimationLayer` retrieves it by layer id.
- `triggerAnimation(ResourceLocation[, startTick])`, `stopTriggeredAnimation`, `stop`, and `replaceAnimationWithFade` are the play/correction/stop/transition surfaces.
- JSON files are discovered below `assets/<namespace>/player_animations/`. Animation ids come from keys in the JSON `animations` object, not filenames.
- The universal loader supports Blockbench/Bedrock/GeckoLib JSON plus the legacy player-animator format. This change authors original Blockbench/Bedrock-style JSON and does not use a legacy tutorial or asset.
- PAL supplies `FirstPersonMode.THIRD_PERSON_MODEL` and `FirstPersonConfiguration` for arm/item rendering. A real-client probe found that mode unusable for this set because the ready sword floated near screen center and the attack arm/sword clipped at excessive scale. First-person combat therefore uses a separate Qingfeng item-render transform rather than forcing third-person body keyframes into camera space.
- PAL's mixin declaration contains only `client` entries, but the mod metadata requires the jar on both physical sides. Dedicated-server side safety is therefore a runtime gate, not an assumption.

This is intentionally a high-impact cross-cutting change. The same OpenSpec change owns PAL proof, the Qingfeng Sword, persistence, networking, combat runtime, client input/animation, resources, validation, and handoff, but implementation advances through Gate A, Gate B, and Gate C rather than cloning five unproven actions at once.

## Goals / Non-Goals

**Goals:**

- Prove the exact supplied PAL jar compiles, starts on the client and dedicated server, loads an original animation, registers one controller per client player, plays, stops, and restores vanilla pose before combat depends on it.
- Add a diamond-tier Qingfeng Sword that is independent of the rideable flying sword and remains a normal vanilla sword in vanilla combat mode.
- Persist only the server-owned vanilla/cultivation preference; keep all action/combo/hit data transient and server-owned.
- Preserve vanilla behavior for every unsupported item even while cultivation mode is selected.
- Implement a deterministic five-move connected sword sequence with one-input-per-move progression, one-slot buffering, server-tick timing, authoritative hit volumes/damage/step movement, and nearby-player animation synchronization.
- Preserve normal damage events, protection, invulnerability, PvP, relevant item/enchantment callbacks, knockback/fire effects, and one declared durability cost without producing a vanilla duplicate or undeclared vanilla sweep.
- Keep PAL imports in physical-client combat code and prove the common/server path does not resolve PAL client types.
- Provide focused tests, negative-fixture validation, jar checks, bounded startup smokes, and explicit manual results.

**Non-Goals:**

- Epic Fight, Better Combat, old PlayerAnimator, or copied third-party code, models, textures, or animations.
- Dodging, blocking, parrying, stamina, poise, super armor, complex hurt animation, launchers, air combos, sword projectiles, spiritual-power cost, realm scaling, proficiency, special techniques, flying-sword combat, lock-on, or global mob combat replacement.
- GeckoLib item models, a new weapon-render model, a custom datapack registry in this first slice, or client-authored action completion/hit/movement state.
- Claiming first-person, visual quality, remote animation, or gameplay feel as accepted without real-client observation.

## Decisions

### Resolve the local PAL jar explicitly and declare the real dependency

`build.gradle` resolves only the exact root filename. Configuration fails with a concise `GradleException` naming that file and the expected placement when it is absent. The jar is an `implementation` dependency so both development client and server runs receive it. `neoforge.mods.toml` declares required `player_animation_library` `[1.1.4,1.2)` on `BOTH`, matching the inspected metadata instead of guessing an id.

The jar remains untracked. This avoids silently introducing a third-party binary into source control even though MIT redistribution is legally available. A future repository-owner decision may replace this local-file route with the official Redlance Maven coordinate or explicitly vendor the jar with its license; neither happens implicitly here.

### Gate A is a hard dependency boundary

The first implementation slice contains only dependency wiring, client-isolated controller registration, one original smoke animation, play/stop entry points, resource validation, client startup evidence, and dedicated-server startup evidence. Combat runtime work does not begin while Gate A has a compile, loading, controller, playback, stop, pose-restoration, or side-safety failure.

The controller factory runs inside the MyVillage client mod constructor's `FMLClientSetupEvent#enqueueWork`, uses a gameplay priority of `1600`, and returns `PlayState.STOP` from its state handler so animations run only when explicitly triggered. `CombatAnimationController` retrieves the layer for any `AbstractClientPlayer`, starts at a server-corrected PAL tick offset, uses short PAL fades between ready/attack states where supported, and calls `stopTriggeredAnimation` followed by `stop` for authoritative cancellation. PAL imports remain below `com.example.myvillage.client.combat`.

### Use PAL's Blockbench/Bedrock path and treat first person conservatively

One authored JSON set under `assets/myvillage/player_animations/` contains `sword_mode_enter`, `sword_ready_idle`, and the five named attack ids. Attack lengths equal the move contract in seconds (`ticks / 20.0`) and each animation moves torso, both shoulders/arms, and legs; the fifth includes a visible step. `sword_ready_idle` loops and attacks play once.

PAL's own source labels the vanilla first-person path fragile, so this design does not claim reliable custom first-person support from API existence alone. Gate A established third-person play/transition/stop and pose recovery. A later real-client `THIRD_PERSON_MODEL` probe failed because its ready sword floated near screen center and its enlarged arm/sword clipped during attack. The PAL controller therefore stays on `FirstPersonMode.DISABLED`.

Canceling the mapped attack event and calling `setSwingHand(false)` also removed every first-person click response. The client keeps the inherited two-argument `LivingEntity#swing(hand, false)` exactly once for an eligible unblocked prediction, or when an unpredicted authoritative buffered move starts, as a packet-free fallback. That overload only advances the local swing pose on a client level; it does not use `LocalPlayer#swing(hand)` or construct a vanilla serverbound swing packet.

The Qingfeng item additionally registers one physical-client `IClientItemExtensions` implementation through `RegisterClientExtensionsEvent`. During a local cultivation-mode main-hand attack, `applyForgeHandTransform` replaces only the vanilla held-item transform with one of five bounded camera-space pose curves. Move selection and elapsed time come from the same predicted start followed by authoritative move-id/start-tick correction that drives PAL; stop/rejection clears both layers. Each curve starts and ends at the vanilla idle transform, has a distinct thrust/cut silhouette, and uses the move's server-owned duration. It never moves the camera, animates a remote player, or adds a network field. Thus third-person full-body PAL playback and first-person held-sword playback coexist without reusing the rejected third-person-model renderer. Custom PAL body arms/camera remain unsupported.

The first owner review found that the initial five first-person curves read as only two clearly different actions. The revision does not alter server cadence, active windows, damage, step timing, or any payload. Instead, it scales every camera-space displacement around the neutral item pose by exactly `1.20`, begins the visible wind-up in the normalized `0.12-0.16` range, places the strike keyframe in `0.56-0.60`, and keeps a recovery silhouette active through `0.84-0.88` before returning to neutral at `1.00`. This redistributes the same server-owned total duration from near-neutral easing into the visible stroke, so the perceived inter-action gap becomes shorter while each main motion takes slightly longer. Uniform amplification is not itself proof that all five silhouettes are readable: the physical-client review must still reject clipping, near-duplicate trajectories, and uncomfortable near-camera rotation.

### Model combat preference as its own persistent attachment

`CombatMode` is an enum with string `Codec` values `vanilla` and `cultivation`. Immutable `CombatPreference` contains only `combatMode`, defaults to vanilla, and owns a codec. `CombatAttachments.PREFERENCE` serializes it and uses `copyOnDeath`. Only `CombatService` replaces this attachment and synchronizes the owning client on login, respawn, dimension change, and accepted toggle.

No combat field enters `CultivationProfile`. The client cache is read-only and cannot install a mode on the server. A toggle payload carries no requested mode; the server derives the opposite of its current stored value, rate-limits the request, clears or preserves recovery locks as defined below, sends an owning-client snapshot, and displays a translatable action-bar result.

### Keep action state in a pure server-tick session machine

`CombatSessionManager` owns UUID-keyed `CombatSession` instances and bounded recovery locks. A session records current move id/index, action start tick, active-window state, at most one buffered intent, hit entity ids, monotonically increasing revision, weapon item id, and stop reason. The pure transition type accepts server game ticks and emits start/active/end/reset decisions; event handlers do not reproduce combo logic.

An idle legal intent starts move one. The final recovery portion of a move accepts one buffered intent; another intent is rejected without replacing it. An intent after action end but before combo timeout starts the next move. Too-early or rate-limited input cannot advance a move. A missed move still advances. Move five completion and combo timeout reset to move one.

Switching item, toggling to vanilla, death, logout, dimension change, mounting, starting meditation/advancement, or entering another disallowed state clears the session. Clearing an unfinished action retains its `blockedUntilTick` recovery lock, so mode/item swapping cannot cancel recovery and immediately attack. Death/logout/dimension teardown may discard the old-world lock because no same-life immediate exploit remains.

### Centralize immutable first-version move definitions

A Java definition graph is used instead of a custom datapack registry in this first slice:

- `CombatStyleDefinition` owns the supported item set, combo timeout, input-buffer policy, and ordered moves.
- `AttackMoveDefinition` owns id, display key, total ticks, inclusive active ticks, multiplier, maximum targets, range, animation id, hitbox definition, optional step, and knockback.
- `HitboxDefinition` owns per-active-tick local samples and tolerance.
- `AnimationDefinition` owns the PAL id and expected tick length.

`BasicSwordStyle` is the only definition owner. Validators enforce one-to-one move/animation ids and duration parity. This structure is intentionally migration-ready for datapack JSON but avoids adding a registry before one style proves the schema.

Initial values remain:

| Move | Chinese name | Total | Active | Multiplier | Max targets | Range |
|---|---|---:|---:|---:|---:|---:|
| `basic_sword_01_thrust` | 青锋问路 | 11 | 3-4 | 0.90 | 1 | 3.0 |
| `basic_sword_02_horizontal_cut` | 流云横渡 | 13 | 4-6 | 0.95 | 3 | 2.8 |
| `basic_sword_03_rising_cut` | 燕返撩月 | 15 | 5-7 | 1.00 | 2 | 2.8 |
| `basic_sword_04_diagonal_cut` | 回风落雁 | 17 | 6-8 | 1.10 | 3 | 3.0 |
| `basic_sword_05_lunge_thrust` | 一线穿云 | 20 | 7-9 | 1.25 | 2 | 3.5 |

### Intercept the mapped attack action, not a physical mouse button

A `Dist.CLIENT` subscriber handles cancelable `InputEvent.InteractionKeyMappingTriggered`. It acts only when `event.isAttack()`, no screen is open, the local player is alive, the read-only snapshot says cultivation mode, and the main hand is the registered Qingfeng Sword. It cancels the event, calls `setSwingHand(false)`, sends one attack intent on the triggered click, applies a bounded local prediction, and starts one packet-free local hand swing only when no action is already active. An accepted buffered move receives the same visual response at its authoritative start. It never checks a GLFW mouse-button constant and therefore follows the user's remapped attack key.

Vanilla mode never cancels. Cultivation mode with a pickaxe, another weapon, an empty hand, or any unsupported item never cancels. Cancellation prevents both vanilla entity attack and sword block-mining start only for the supported cultivation-mode sword. Client debounce plus server rate limiting prevents held/repeat input from sending every tick.

### Extend the existing input-only payload architecture

Protocol version increments once for the new payload set:

- `CombatModeTogglePayload` C2S: empty intent.
- `CombatModeSnapshotPayload` S2C: authoritative mode and preference revision for the owning client.
- `SwordAttackIntentPayload` C2S: empty attack intent.
- `CombatAttackStartPayload` S2C: attacker entity id, move id, server start tick, and action revision.
- `CombatAttackStopPayload` S2C: attacker entity id, revision, and bounded stop reason.

C2S handlers derive the sender and revalidate life/removal/spectator state, mode, exact main-hand item, dimension/world, mount/cultivation conflicts, current server tick, session transition, and packet rate. They accept no combo index, target, damage, hitbox, endpoint, velocity, or completion value. Start/stop payloads are sent to the attacker and tracking players. Client receivers are common-safe bridges installed only by a client subscriber.

Local prediction never becomes authority. It predicts only an animation from the last authoritative read-only sequence state. The next start payload restarts/corrects to the server move and elapsed tick; a stop or rejection revision cancels an unconfirmed prediction. Remote clients animate only from server broadcasts.

### Resolve move-specific swept volumes on the server

Each active server tick transforms local samples by the server player's feet position and body yaw. Broad phase queries one union AABB. Narrow phase uses yaw-oriented boxes and segment/capsule-style swept tests against candidate entity AABBs:

- Move 1 uses a narrow center-line thrust with increasing forward samples.
- Move 2 uses several right-to-left horizontal OBB samples spanning about 110 degrees.
- Move 3 uses rising diagonal samples from left-low to right-high.
- Move 4 uses a thicker right-high to left-low diagonal sweep that remains narrower than move 2.
- Move 5 uses the union of the blade thrust and the complete server-observed player-start-to-player-end swept volume.

Horizontal tolerance is at most `0.25` block and vertical tolerance at most `0.15` block. Candidate order is deterministic by first contact distance then entity id. The resolver filters the attacker, dead/removed/spectator/invulnerable-invalid candidates, world mismatch, team/PvP-forbidden candidates, previously hit ids, and candidates behind a solid-block clip. It stops at the move maximum. One entity can be damaged only once in one action.

Move five requests at most `0.8` block forward movement. Before moving, the server clips the intended path, checks collision-free player space and supporting collision below the destination to avoid a ledge step, then uses normal collision-aware server movement for the safe distance. No client movement value is accepted. The resolver records the actual start and end, so collision shortening cannot leave a fake long hit sweep.

An operator-only `/myvillage combat debug on|off` flag is transient and defaults off. When enabled, the server emits bounded particles for active samples and accepted hits; it exposes no authoritative state to the client.

### Use an explicit damage service rather than `ServerPlayer#attack`

Calling `player.attack(target)` is rejected for cultivation moves because it would apply vanilla cooldown scaling, critical/sprint decisions, reset timing, and possible `SWORD_SWEEP`; repeated calls for multi-target moves would also reset attack strength and damage the weapon per target. Those side effects conflict with move-owned timing, hit shape, target cap, and durability rules.

`CombatDamageService` instead follows the mapped 1.21.1 hooks deliberately:

1. Fire `CommonHooks.onPlayerAttackTarget` for each selected target, preserving `AttackEntityEvent` cancellation and the item's left-click hook.
2. Revalidate attackability and skip-attack interaction.
3. Start from the player's current `Attributes.ATTACK_DAMAGE`, multiply the attribute portion by the move multiplier, add item target-specific attack bonus, and call `EnchantmentHelper.modifyDamage` with the current weapon and `playerAttack` damage source.
4. Call `target.hurt(player.damageSources().playerAttack(player), damage)`, retaining NeoForge incoming/pre/post damage events, armor, protection, invulnerability frames, and `ServerPlayer` PvP checks.
5. On success, use current attack-knockback attribute plus `EnchantmentHelper.modifyKnockback`, apply only the move's declared small knockback, and call `EnchantmentHelper.doPostAttackEffects` for effects such as relevant attacker/victim enchantment callbacks.
6. After the action's first successful target, run the sword's `hurtEnemy`/`postHurtEnemy` path exactly once for that action, update last-hurt/stat/exhaustion bookkeeping once, and retain normal break/repair/Mending behavior.

Cultivation attacks intentionally do not produce vanilla critical, sprint-knockback, or sweeping attacks. Vanilla mode still uses unmodified `player.attack` through normal input. Damage-service parity and known exclusions are tested and documented rather than described as byte-for-byte vanilla equivalence.

### Integrate cultivation conflicts without merging state models

An accepted sword-attack intent is routed through the existing meditation/advancement gameplay interruption path. If a cultivation session was active, it ends once and the same intent does not also start combat; a later intent may start after eligibility is re-evaluated. Conversely, an accepted meditation or advancement start calls combat cleanup and preserves any unfinished action recovery lock. Combat never writes cultivation profile data, and cultivation never writes combat preference directly.

### Implement the Qingfeng Sword as a normal mapped diamond-tier sword

`ModItems` registers a `SwordItem` with `Tiers.DIAMOND` and `SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)`, exactly matching the mapped vanilla diamond-sword construction. `TieredItem` supplies durability `1561`, diamond enchantment value `10`, and diamond repair ingredient; the attributes yield player attack damage `7.0` and attack speed `1.6` while held. The item joins the vanilla swords tag so normal sword enchantment and repair ecosystems recognize it.

The item has one ordinary generated item model, a new original transparent pixel texture depicting a narrow Chinese double-edged straight sword with a small guard, dark wrap, and cyan-jade accent, standard diamond-sword-shaped recipe, bilingual names, and creative-tab exposure next to the rideable sword. It does not reuse the rideable item/entity/model behavior and does not add GeckoLib.

## Risks / Trade-offs

- [PAL 1.1.4 universal NeoForge entry point references client event types despite `BOTH` metadata] -> Keep all MyVillage PAL imports client-only and require a real dedicated-server startup before Gate A passes; stop with the exact linkage/classloading error if the library itself fails.
- [The local jar makes clean clones non-reproducible] -> Fail with a clear filename/path message and document the SHA/license/source; do not silently fall back to a different version. A Maven migration is a later explicit decision.
- [PAL first-person rendering can be uncomfortable or incompatible] -> The real-client `THIRD_PERSON_MODEL` probe failed, so keep PAL custom first person disabled and use a bounded Qingfeng-only `IClientItemExtensions` pose layer driven by the same corrected move timeline.
- [Client prediction can show the wrong move under latency] -> Carry server start tick and revision, restart at corrected elapsed tick, and stop stale predictions.
- [A custom damage path can miss obscure vanilla/mod hooks] -> Invoke the mapped NeoForge attack gate, vanilla enchantment helpers, standard player damage source, ordinary `hurt`, post-attack helpers, and item durability hooks; test documented parity and keep critical/sweep exclusions explicit.
- [Multi-target invulnerability frames may reject closely spaced moves] -> Preserve normal invulnerability behavior as required; tune move cadence only through centralized definitions, never by bypassing invulnerability.
- [OBB/swept-volume math can overreach or miss corners] -> Unit-test transforms/intersections, use constrained tolerances, deterministic samples, wall clips, and opt-in debug particles; leave real-client ranges pending until observed.
- [Fifth-move stepping can cross geometry or ledges] -> Server clip/collision/support checks precede bounded normal movement, and actual displacement bounds the hit sweep.
- [Mode or weapon switching can cancel recovery] -> Preserve a server recovery lock independently of the cleared action session and test both exploits.
- [Seven hand-authored animations may be technically valid but aesthetically weak] -> Validate format/duration/bones automatically, inspect the source texture/animation evidence, and require a human visual/gameplay verdict rather than self-accepting it.

## Migration Plan

1. Complete and strictly validate OpenSpec artifacts plus the functional Item Contract.
2. Implement Gate A only: local jar resolution, dependency metadata, client PAL layer, original smoke resource, focused loader/controller checks, client startup, play/stop/pose recovery observation, and dedicated-server startup.
3. If Gate A is green, implement Gate B: Qingfeng Sword, preference, input interception, first move, payloads, authoritative timing/hit/damage, remote start/stop, and cleanup; verify the vertical slice before adding other move definitions.
4. Implement Gate C: remaining animations/moves, full combo buffer/reset, move-specific volumes, fifth step, debug particles, and multiplayer/lifecycle behavior.
5. Run focused validators and negative fixtures, all relevant cultivation/flying-sword regressions, Gradle tests/build, jar inspection, bounded server/client smokes, then update the manual ledger with only observed results.
6. Apply the release rule from `openspec/config.yaml`, rerun release-sensitive gates, record the visual/gameplay verdict, sync specs, archive, fast-forward to `main`, and push only when every required gate and owner decision is complete.

Rollback removes MyVillage's PAL dependency declaration/wiring, combat classes/resources/contracts, attachment/payload registrations, and Qingfeng item. Existing cultivation and flying-sword data remain readable because their codecs and ids are not reused. The independent combat preference attachment may be absent without affecting `CultivationProfile`.

## Open Questions

- The PAL controller uses `FirstPersonMode.DISABLED`; the failed `THIRD_PERSON_MODEL` probe does not block the separate five-move Qingfeng held-item layer, but custom PAL body arms/camera remain unsupported.
- Gate B must confirm the minimal explicit damage path fires the expected NeoForge/item/enchantment hooks under the actual runtime. Any missing required hook changes the service before moves two through five are copied.
- The final visual verdict may request small timing/sample/pose adjustments. Such tuning stays in centralized definitions/animation JSON and must not broaden the fixed non-goals.
