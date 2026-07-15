# PAL Sword Combat Integration

Factual integration note for change `add-sword-combat-foundation`.

See also:

- OpenSpec: [`player-animation-integration`](../../openspec/changes/add-sword-combat-foundation/specs/player-animation-integration/spec.md)
- OpenSpec: [`sword-combat-foundation`](../../openspec/changes/add-sword-combat-foundation/specs/sword-combat-foundation/spec.md)
- Item route: [Mod Item Creation](22_mod_item_creation.md)
- Existing network reference: [Rideable Flying Sword](27_rideable_flying_sword.md)
- Current cultivation runtime: [Cultivation Playable Loop](30_cultivation_playable_loop.md)

## Supplied Artifact

| Field | Verified value |
|---|---|
| Root filename | `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` |
| SHA-256 | `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072` |
| NeoForge mod id | `player_animation_library` |
| Version | `1.1.4+mc.1.21.1` |
| Display name | `Player Animation Library` |
| Java packages | `com.zigythebird.playeranim`, `com.zigythebird.playeranimcore` |
| NeoForge requirement | `[21.1,)` |
| Minecraft requirement | `[1.21.1,)` |
| Declared side | `BOTH` |
| License | MIT License, copyright 2025 ZigyTheBird |
| Official docs | <https://docs.zigythebird.com/pal/intro> |
| Exact official source branch | <https://github.com/ZigyTheBird/PlayerAnimationLibrary/tree/1.21.1> |

These values come from the local jar, not an old PlayerAnimator tutorial or a
guessed dependency name.

## Local Inspection

The inspected jar contains no source examples, but it contains the production
classes, NeoForge metadata, mixin config, embedded license, and public APIs.
The exact official `1.21.1` branch has `mod_version = 1.1.4` and supplies source
for the same classes and animation test resources. Official PAL documentation
also lists `1.1.4+mc.1.21.1` as the supported 1.21.1 release.

Key classes:

- `com.zigythebird.playeranim.api.PlayerAnimationFactory`
- `com.zigythebird.playeranim.api.PlayerAnimationAccess`
- `com.zigythebird.playeranim.animation.PlayerAnimationController`
- `com.zigythebird.playeranim.animation.PlayerAnimResources`
- `com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder`
- `com.zigythebird.playeranimcore.animation.AnimationController`
- `com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier`
- `com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode`
- `com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration`

## Controller API

NeoForge registration must run from client setup work:

```java
PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
        LAYER_ID,
        1600,
        player -> new PlayerAnimationController(
                player,
                (controller, state, setter) -> PlayState.STOP));
```

PAL's official docs warn that NeoForge factory registration must be called
inside `FMLClientSetupEvent#enqueueWork`. Factory priority controls conflicts:
low values are idle-style layers, `1000` is cosmetic, and `1500+` is intended
for important gameplay animation. MyVillage therefore reserves `1600` for the
sword-combat layer.

Retrieve and control one player's layer:

```java
PlayerAnimationController controller =
        (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(
                player, LAYER_ID);
controller.triggerAnimation(animationId, elapsedTicks);
controller.stopTriggeredAnimation();
controller.stop();
```

Relevant transition APIs are:

- `triggerAnimation(ResourceLocation)`
- `triggerAnimation(ResourceLocation, float startAnimFrom)`
- `replaceAnimationWithFade(AbstractFadeModifier, ResourceLocation)`
- `AbstractFadeModifier.standardFadeIn(...)`
- `stopTriggeredAnimation()`
- `stop()`
- `forceAnimationReset()`

The controller is created for each client-side `AbstractClientPlayer`, so the
same layer supports the local player and tracked remote players. MyVillage's
server broadcasts decide remote playback; PAL itself is not the combat network
protocol.

## Animation Resources

PAL's resource reload listener calls:

```text
ResourceManager.listResources("player_animations", ... .json)
```

The verified path is therefore:

```text
assets/<namespace>/player_animations/<file>.json
```

Runtime ids use the resource namespace plus the key inside the JSON
`animations` object. The filename does not choose the animation id.

The universal loader accepts:

- Blockbench/Bedrock animation JSON;
- GeckoLib-format Blockbench JSON;
- Blender JSON supported by PAL;
- legacy player-animator format.

MyVillage authors original Blockbench/Bedrock-style JSON. It does not copy PAL
test animations or assets from Epic Fight, Better Combat, PlayerAnimator, or
another mod.

The seven required keys are:

```text
sword_mode_enter
sword_ready_idle
basic_sword_01_thrust
basic_sword_02_horizontal_cut
basic_sword_03_rising_cut
basic_sword_04_diagonal_cut
basic_sword_05_lunge_thrust
```

Attack animation length is authored in seconds and must remain aligned to
`total_ticks / 20.0` in `BasicSwordStyle`.

## First-person Conclusion

PAL 1.1.4 exposes:

- `FirstPersonMode.NONE`
- `FirstPersonMode.VANILLA`
- `FirstPersonMode.THIRD_PERSON_MODEL`
- `FirstPersonMode.DISABLED`

`THIRD_PERSON_MODEL` suppresses the vanilla hand renderer and renders selected
player-model arms/items according to `FirstPersonConfiguration`. PAL's own
source describes the vanilla first-person mode as frequently broken and its
third-person-model render path as compatibility-sensitive. API availability is
therefore not acceptance evidence.

Gate A proved third-person play/transition/stop and pose restoration. A later
real-client `THIRD_PERSON_MODEL` probe showed an unusable floating center sword
in ready state plus an oversized, clipping arm/sword during attack. The shipped
controller therefore keeps `FirstPersonMode.DISABLED`; PAL-rendered body arms
and camera transforms are unsupported rather than promoted from API
availability. This rejection does not require first-person combat itself to be
absent.

First-person Qingfeng attacks use a separate NeoForge
`IClientItemExtensions` implementation registered through
`RegisterClientExtensionsEvent`. `QingfengFirstPersonAnimator` overrides only
the main-hand Qingfeng transform while a cultivation action is active. Its five
bounded `FirstPersonSwordPose` curves map one-to-one to `BasicSwordStyle` and
sample the same `totalTicks` timeline. Local prediction starts the matching
curve immediately; the authoritative start replays it from corrected elapsed
ticks, and rejection, interruption, or completion clears it. The extension
does not move the camera, render PAL body arms, send a payload, or own any hit,
damage, combo, or step decision.

That extension only transforms the item; NeoForge's non-empty item branch does
not render a player arm. `ClientCombatBootstrap` therefore registers
`QingfengFirstPersonArmRenderer` directly on `RenderHandEvent`. The listener
derives its pose from `QingfengFirstPersonAnimator.currentFrame`, falls back to
the same neutral pose, draws before the normal item pass, and never cancels that
pass. `QingfengFirstPersonArmModel` builds an original viewmodel hierarchy
instead of borrowing PAL-mutated, shared `PlayerRenderer`, or complete vanilla
player-model parts. Its non-rendering `upper_arm` node drives a visible
`forearm`, then a visible `hand`; a separate `connector` runs from below the
screen to the computed elbow. Separate skin and sleeve chains use the current
player texture, respect sleeve visibility, and are generated for wide/slim and
right/left arms. Internal segment caps are omitted so a face cannot become a
near-plane slab.

Every move authors shoulder, elbow, and wrist rotations on the same corrected
item frame. Forward kinematics computes the distal hand endpoint and applies a
three-dimensional correction that pins it to the sword grip for either hand.
The same corrected chain computes the elbow connector target. During active
motion, the viewmodel scales smoothly around the grip from `1.00` to `0.45` by
normalized progress `0.12`, remains compact through the middle, and restores
`1.00` at both neutral endpoints; the elbow target uses that same scale. A
rejected predecessor damped a complete arm around the wrong origin and separated
the hand from the handle. Its pivot-locked successor fixed the grip but exposed
the entire arm as a floating middle-screen cuboid. The segmented hierarchy
replaces both forms. It is MyVillage-owned code and adds no Epic Fight or
GeckoLib code, assets, runtime dependency, or animation authority.

Main-hand, visibility, cultivation-mode, and Qingfeng guards suppress the layer
everywhere else. It has no independent clock, packet, camera, remote-player,
hit, damage, or movement authority.

The original intercepted path canceled the mapped event and set its hand swing
false, so it also removed every visible first-person response. The final client
keeps that event suppression but calls inherited
`LivingEntity#swing(InteractionHand.MAIN_HAND, false)` once for an eligible
unblocked prediction, or at an unpredicted authoritative buffered start. Unlike
the one-argument `LocalPlayer#swing`, this client-level overload does not send a
vanilla swing packet. It remains a small packet-free input fallback underneath
the dedicated held-item curve; hit, damage, movement, move choice, and action
completion stay server-owned.

The first owner review rejected the initial claim that all five first-person
paths were visually distinct: in motion they read as only two clear action
families. The first revision kept every server `totalTicks`, active window,
damage, step, and payload unchanged while amplifying translation, rotation, and
scale displacement around neutral by exactly `1.20`. Wind-up keyframes moved to
normalized progress `0.12-0.16`, strike keyframes to `0.56-0.60`, and late
recovery keyframes to `0.84-0.88`, before exact neutral at `1.00`. The timing
ranges remain current, but the fixed factor is now implementation history.

A developer physical-client smoke exercised all five current moves with the
segmented skin and sleeve hierarchy. It showed distinct elbow/wrist poses, the
arm entering from the screen edge, the handle remaining at the corrected hand
endpoint, and neutral recovery without the former complete floating arm or
near-plane slab. The connector remains a simple cuboid whose width and anatomy
need owner review. This evidence does not promote the full grip or visual ledger.

The active viewport contract calibrates each move independently under a
`960x540`, `16:9`, FOV-70 reference capture. The temporal union of the projected
sword-and-arm silhouette from visible wind-up through late recovery must span
at least `0.50` of the viewport on one screen axis, intersect the central
horizontal band `x=[0.35,0.65]`, and not remain wholly inside the lower-right
quadrant. This is an accumulated action path, not a single-frame half-screen
occlusion target. The shared parent frame, wrist-pivot grip, normalized timing
ranges, server timing, damage, step, and payload remain unchanged; near-plane
clipping, grip separation, duplicate arms, and camera rotation remain failures.
Only owner follow-up can promote the replacement from `not_verified`.

## Side Boundary

PAL metadata requires the library on both sides. Its mixin JSON declares all
listed mixins under the `client` array and none under common `mixins`. Its
NeoForge entry point nevertheless has method signatures using NeoForge client
events. That makes a real dedicated-server startup mandatory.

MyVillage's side rule is stricter:

```text
com.example.myvillage.client.combat/**
```

is the only intended PAL import owner. Common item, attachment, payload,
session, hitbox, damage, and lifecycle code passes resource ids and revisions
without resolving PAL or `net.minecraft.client` types.

## Gradle And Distribution

The first integration resolves the exact root jar as a local file dependency.
Missing-file configuration must fail with a clear message naming the expected
file. MyVillage also declares the inspected required mod dependency in
`neoforge.mods.toml`.

Do not:

- shade PAL into the MyVillage jar;
- unpack or copy its classes;
- silently fetch or substitute another PAL version;
- commit the third-party binary without a separate repository-owner decision.

The embedded MIT license permits redistribution, modification, and commercial
use as long as the copyright and permission notice accompanies substantial
copies. Legal permission does not by itself establish this repository's binary
vendoring policy. The current jar stays untracked. A later explicit decision
may use the official Redlance Maven coordinate or vendor the jar with its
notice.

## Current MyVillage Integration Map

- Items and `myvillage:main`: `ModItems`.
- Client keys: `ClientCultivationKeyMappings` uses configurable `KeyMapping`
  registrations; combat follows the same pattern with default `R`.
- Cancelable mapped attack input: NeoForge 21.1.233
  `InputEvent.InteractionKeyMappingTriggered`, using `isAttack()`,
  `setCanceled(true)`, and `setSwingHand(false)`.
- Payload owner: one `ModPayloads` registrar at protocol `4`; the combat change
  added two empty C2S intents and revisioned S2C mode/start/stop payloads without
  changing existing flying-sword or cultivation payload shapes.
- Existing authority pattern: `FlyingSwordInputPayload` sends only a bounded
  bitset; cultivation sends immutable clientbound snapshots and bounded action
  intents.
- Persistent profile: `CultivationAttachments.PROFILE` uses codec serialization
  and `copyOnDeath`; combat preference is a separate attachment.
- Lifecycle synchronization: `CultivationEvents` covers login, respawn, and
  dimension change; combat preference uses equivalent server-owned snapshots.
- Transient cultivation conflicts: `MeditationManager` owns meditation and
  advancement sessions; combat integrates through interruption/eligibility and
  does not add fields to `CultivationProfile`.
- Physical side: existing client subscribers are `Dist.CLIENT`; the combat PAL
  adapter follows that boundary.

## Damage Route

The mapped 1.21.1 vanilla attack path was inspected before choosing a route.

Calling `ServerPlayer#attack` from every custom active frame is not selected.
It would also run vanilla cooldown scaling, critical/sprint logic, a possible
`SWORD_SWEEP`, attack-strength reset, and one item durability callback per
target. Those behaviors conflict with the five move definitions and custom
target caps.

`CombatDamageService` instead uses the narrow mapped surfaces:

1. `CommonHooks.onPlayerAttackTarget` for `AttackEntityEvent` and the item
   left-click hook;
2. current `Attributes.ATTACK_DAMAGE` times the move multiplier;
3. item target bonus and `EnchantmentHelper.modifyDamage`;
4. `player.damageSources().playerAttack(player)` plus ordinary `target.hurt`;
5. current attack-knockback attribute, `EnchantmentHelper.modifyKnockback`, and
   frozen action facing plus the vanilla server-player motion packet/reset path,
   then `EnchantmentHelper.doPostAttackEffectsWithItemSource` after successful
   damage;
6. one `hurtEnemy`/`postHurtEnemy` durability path for the whole action after
   its first successful target.

This retains the standard hurt pipeline, armor/protection, invulnerability,
PvP checks, NeoForge incoming/pre/post damage events, and the mapped
data-driven enchantment helpers. Cultivation mode deliberately excludes vanilla
sweeping, critical, sprint bonus, and cooldown scaling. Vanilla mode still uses
the untouched vanilla path.

## Timing And Hitbox Tuning

`BasicSwordStyle` is the only owner of the five move contracts. Initial totals
are `11/13/15/17/20` ticks, active windows are `3-4/4-6/5-7/6-8/7-9`, and the
late buffer begins at `8/10/12/14/17`. Combo timeout is 14 server ticks and the
minimum accepted intent interval is two server ticks.

The five shape families are center thrust, approximately 110-degree horizontal
arc, rising diagonal, thicker descending diagonal, and long lunge thrust. Every
active sample uses `0.20` horizontal and `0.12` vertical tolerance. Broad-phase
union bounds are followed by segment/capsule-style narrow tests, wall clips,
legal-target filtering, deterministic contact-distance/entity-id ordering, an
action-wide target cap, and attempted-target deduplication. Move five requests a
server-owned step at tick 6, samples down to a safe supported destination, uses
normal collision-aware movement, and adds only the actual start-to-end sweep.

## Verified Commands

Completed inspection and Gate A commands:

```text
sha256sum PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar
jar tf PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar
unzip -p PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar META-INF/neoforge.mods.toml
unzip -p PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar LICENSE
javap -classpath PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar ...
git clone --depth 1 --branch 1.21.1 https://github.com/ZigyTheBird/PlayerAnimationLibrary.git /tmp/player-animation-library-1.21.1
openspec validate add-sword-combat-foundation --type change --strict
python3 tools/genops/validate_pipelines.py
./gradlew compileJava
./gradlew runAcceptanceServer
./gradlew runClient -Ppal_smoke_world=pal_gate_a
/myvillage_pal_smoke move 1
/myvillage_pal_smoke move 2
/myvillage_pal_smoke move 3
/myvillage_pal_smoke move 4
/myvillage_pal_smoke move 5
```

Gate A result: `pass`.

The exact jar compiled; the physical client registered the priority-1600 layer,
loaded the authored smoke resource, accepted play and fade transition, stopped
an active trigger, and restored the normal pose. The bounded dedicated server
reached ready state and stopped cleanly with PAL present. No PAL/MyVillage
client-only linkage or mixin failure was observed. The client host lacked an
OpenAL device and existing plaque blockstate warnings remained, neither of which
was a PAL failure.

Gate B result: `pass`. A physical client exercised Qingfeng -> cultivation
mode -> intercepted empty attack intent -> authoritative move one -> active hit
-> real damage and one durability loss -> revisioned stop. A second physical
client observed the attack start/stop and target damage.

Gate C result: `pass`. Both clients observed the exact five-move order without
placeholder aliases. The bounded target changed from `200.0` to `164.1824`
health, the sword lost exactly five durability, and move five advanced the
server player by exactly `0.8` blocks. Separate wall evidence retained target
health and sword durability, and mode/item/mount/dimension/death/meditation
interruptions stopped the active session.

Release validation on `0.26.0` passed strict OpenSpec validation, CRAFT pipeline
and per-owner front-door checks, Item Contract schema validation, the focused
and aggregate validators, 139 validator-pattern tests, 168 full Python tests,
200 Gradle tests/build, practical jar inspection, dedicated-server startup/clean
stop, and a final physical-client startup/play/transition/stop/clean-exit smoke.
Reconnecting after a clean server restart restored cultivation mode and ready
idle without another toggle.

The dedicated first-person smoke used the actual Qingfeng item and directly
observed all five current held-item paths plus the local segmented skin/sleeve
viewmodel and normal-pose recovery. Historical revisions separately failed for
hand/handle separation and for presenting a complete arm as a floating cuboid.
The current evidence shows authored shoulder/elbow/wrist motion, distal grip
correction, and a screen-edge connector without the former full-arm slab,
duplicate arm, or stuck transform. It validates the implementation route, not
attack input or server gameplay; the local command sends no combat intent. The
segmented arm join and per-move half-viewport envelope remain `not_verified`
until owner review. PAL body arms/camera remain disabled with
`FirstPersonMode.DISABLED`.

The follow-up viewport capture used a real mapped `J` combo at `960x540`,
`16:9`, FOV 70 rather than `/myvillage_pal_smoke`. Each independently calibrated
path visibly left the lower-right hold, entered the center/left region, and
returned to neutral without changing the server timeline. The current active
viewmodel eases to `0.45` uniform scale by normalized progress `0.12` around the
distal grip, and the corrected elbow target follows the same scale. Combined
with side-only segment faces, this removed the earlier near-plane sleeve slab in
the developer capture. That observation does not promote the owner's five-move
viewport, joint shape, connector proportion, or grip ledger to `pass`.

These technical gates do not settle combat feel. Exact manual
range/cap/knockback and enchantment/event compatibility surfaces remain
`not_verified` in README. The texture and animation owner verdict is still
pending and cannot be inferred from automated or developer-client evidence.
