# PAL Sword Combat Integration

Factual integration note for the Qingfeng sword combat. Its OpenSpec change
`add-sword-combat-foundation` was deleted unarchived on 2026-10-02 and has no
specs under `openspec/specs/`; the last tree that has it is commit `fb407f1`.

See also:

- Item route: [Mod Item Creation](22_mod_item_creation.md)
- Existing network reference: [Rideable Flying Sword](27_rideable_flying_sword.md)
- Current cultivation runtime: [Cultivation Playable Loop](30_cultivation_playable_loop.md)
- Framework comparison and direction: [Combat Framework Comparison (Combat Lab)](33_combat_framework_comparison.md)
- Move and weapon data, validator, and capture tooling since 0.28.0: [Combat Data and Capture Tooling](34_combat_data_and_capture.md)
- The second weapon (two-handed spear, 0.29.0) and the validation findings: [Lingxiao Spear](35_lingxiao_spear.md)

Since 0.28.0 the move values quoted below live in
`data/myvillage/combat/style/basic_sword.json`, not in Java constants, and the
runtime picks the style by held item. The values themselves did not change.
Since 0.29.0 a second weapon, the Lingxiao Spear, uses the same runtime; the
hit-stop, re-equip, and trail rules below changed with it and apply to
Qingfeng too.

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
`total_ticks / 20.0` in the style file.

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
`RegisterClientExtensionsEvent` for every registered weapon item.
`FirstPersonWeaponAnimator` overrides only the main-hand weapon transform in
cultivation mode. Local prediction starts
the matching swing immediately; the authoritative start replays it from
corrected elapsed ticks, and rejection or interruption blends back to the
neutral hold over three ticks. The extension does not move the camera, render
PAL body arms, send a payload, or own any hit, damage, combo, or step decision.

### Swing rig (0.26.2)

The owner rejected the 0.26.1 curves as "not like swinging a sword". Capture
showed three causes: strike keys at normalized `0.56-0.60` landed after the
server active windows (about `0.27-0.53`), so targets flinched before the blade
arrived; a smoothstep on every segment stopped the blade at each key; and the
flat sprite was edge-on at screen center.

`FirstPersonSwing` now loads `assets/myvillage/combat/qingfeng_first_person.json`
through `FirstPersonSwingResources`, a client reload listener, so `F3+T`
applies edits. The rig swings the sword from one camera-space shoulder pivot:

- `plane`: swing-plane tilt around the view axis (screen angle of the cut).
- `sweep`: arm angle within that plane (positive toward the left).
- `reach`: pivot-to-grip distance.
- `lead`, `lift`, `twist`: blade aim ahead/behind the arm, elevation (`0` up,
  `-90` forward), and turn about the blade (`90` shows the flat on a cut).
- `offset`: optional camera-space translation.

`FirstPersonWeaponTransform` (named `FirstPersonSwordTransform` before the
format 2 rename) applies those in that order. It then multiplies by
the inverse of the baked model's own `firstperson_*` display transform and maps
the geometry contract's `grip_center` onto the grip at `rig.weapon_scale`, so
the rig grip is the Qingfeng handle (0.27.1; see below). The left hand mirrors
the right. Keys are server ticks with per-segment `linear`/`in`/`out`/`in_out`
easing (0.27.0 adds `in_cubic`, `out_cubic`, `in_out_cubic`, and `out_back` with
about 12% overshoot), start and end at `neutral`, and each move's `strike`
window must cover the server active window within three ticks. An optional
per-move `contact` tick (inside the strike window) anchors the hit-stop. The
0.27.0 rig strikes with an eased-in blow, overshoots, holds for 2.7-4 ticks, and
returns over 3.5-4 ticks. A chained move cross-fades from the pose on screen over
2 ticks. Loading rejects anything else, and
`FirstPersonSwingTest` also checks grip depth, strike speed against recovery,
and hand mirroring.

`/myvillage_pal_smoke first_person <move> <tick>` holds one frame and
`first_person release` clears it; combined with `F3+T` this is the tuning loop.

### Feedback

Each move's `feedback` block in the style file gives it a swing sound and
pitch, a hit sound, an optional heavy layer sound, a heavy-hit flag (moves 4 and 5), hit-stop ticks (`1.5/2/2/3/4`), camera trauma
(`0.25/0.30/0.30/0.50/0.80`), and a blade-cut roll (`0/0/-35/40/0` degrees).
`CombatFeedbackService` plays the swing sound one tick before the active start
(`actionTick == activeStartTick() - 1`) for everyone but the attacker, with
`CombatSounds.jitteredSwingPitch` adding about 6% pitch variation. The
attacker's `FirstPersonWeaponAnimator.clientTick` plays the same cue locally
about one tick ahead of the visible strike. After successful damage only, the
service plays the hit sound (plus the `combat.sword.impact_heavy` layer on
heavy moves), spawns one `myvillage:blade_cut` particle at the true contact
point (x velocity carries the roll in radians, y > 0.5 marks heavy), sends 3
crit sparks (6 on heavy), sends the attacker a `CombatHitConfirmPayload`
(attacker id, revision, hit count), and broadcasts `CombatImpactPayload` to
the attacker and all trackers. The vanilla sweep particle was removed.

`SwingClock.beginHitStop(realTick, hitStopTicks)` uses the move's own hit-stop:
the swing is fully frozen for the first 60% of the stop, creeps at `0.15` for
the rest, then catches up so it still ends on the server total. Since 0.29.0
the clock also keeps the action's present, the latest real tick any reader has
seen (a rendered frame, a client tick, a packet), and the pose is drawn from
it, so it never moves backwards along the move. `SwingClock.confirmHit` starts
the stop at the rig's per-move `contact` tick (`3.8/4.9/6.0/7.0/8.0`) if the
drawn swing has not reached it, and otherwise at the present, freezing the pose
already on screen. Before 0.29.0 a late confirmation started the stop at the
tick reading of the packet, which is handled at the start of a frame and lies
up to a tick behind the frame already drawn, so the blade stepped back (the
horizontal cut by about 114 px at normal speed). Each confirmation logs
`PAL_SMOKE fp_hit_stop` with the reading, the present, the contact tick, the
start, and a `result`. Only the first confirmation of an action starts a stop
(a three-target sweep logs one `started` and two
`ignored_stop_already_started`), and a confirmation for an action that is no
longer current is dropped (`ignored_not_current_action`).
`HIT_STOP_TICKS = 2.5F` remains the default.

Client combat clock (0.29.0). The client's `Level#getGameTime()` is not
monotonic: every 20 ticks the server's time packet sets it to the server's
time, a tick or more either way, and every combat timeline used to read it as
elapsed time (a +2 reset skipped the swing two ticks). `ClientCombatClock` is
the client's own count of ticks in which the level ran (paused or tick-frozen
ticks do not count); it advances at the end of the client tick before any
other combat handler. The first-person swing (`LocalSwingTimeline`, which
holds the start, the slew of an authoritative correction, and the
`SwingClock`), prediction, buffering and chain ticks, impact freezes, the
attacker's stop, the world trail, camera kicks, and the arm's lag all read it.
`ClientCombatClock.elapsedSinceServer` is the one conversion of a server tick,
done once when the START arrives; the validator rejects any other
`getGameTime()` in `client/combat`. A reset during a local action logs
`PAL_SMOKE client_time_jump from=<t> to=<t> local=<n>`. Consequence: a client
clock k ticks off the server's at the START draws the whole move k ticks off
(about −1 to +2 on the capture host); the server's STOP cuts the last k
recovery ticks or the next start is slewed, where the next time packet used
to correct it mid-move as a visible skip. An authoritative correction of up
to 2 ticks is slewed over 3 ticks (a larger one snaps); each logs
`PAL_SMOKE fp_resync`.

Both trails use `CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT` (SRC_ALPHA /
ONE_MINUS_SRC_ALPHA, no cull, no depth write). The additive `SWORD_TRAIL`
washed out to a white slab against the sky and was removed. Each trail is a thin
tapered band with a near-white edge at the tip and a pale-blue body inside it;
thrusts draw a single streak. `FirstPersonWeaponTrail` still re-poses the weapon
at earlier visual ticks from the shared frame and never cancels the item pass.
`CombatWorldTrails` still samples the move's hitbox with the broadcast facing
yaw and is skipped in first person; a move may instead name its own trail path
(`trail.samples`, presentation only, read through
`AttackMoveDefinition.worldTrailSamples()`; see
[Combat Data and Capture](34_combat_data_and_capture.md)). It is drawn from a pivot 1.3 blocks up and
holds still while its attacker is frozen in a hit-stop. Samples that share a
server tick are spread evenly through the tick (one sample per tick, as all
Qingfeng moves have, sits on its tick as before).

### Camera and impact effects

`CombatCameraFx` (client, presentation only) adds attacker-local trauma on hit
confirm. Shake is trauma² × (2.5° roll, 1.5° pitch, 1.0° yaw) of about 22 Hz
noise, and trauma decays at 1.6 per second. It also plays per-move kicks: a
rising cut pitches up, a diagonal pitches down and rolls, and the lunge gets a
FOV punch plus a FOV surge at its step. Angles scale with
`options.screenEffectScale` and FOV changes with `options.fovEffectScale`; both
are halved in third person. `onComputeFovModifier` removes the vanilla slowness
zoom that the server's `myvillage:combat_commit` and `myvillage:combat_stun`
movement modifiers would otherwise cause. Other speed changes still change FOV.

`CombatImpactFx` reads `CombatImpactPayload` (attacker id, revision, move id,
struck entity ids, contact points; no damage or health). On the client it skips
struck non-player entities' ticks for the rounded hit-stop, jitters every
struck entity except the local player, and drives a remote attacker's PAL
hit-stop through `CombatAnimationController.setHitStopRate`. None of this sends a
packet or changes an entity's server state. Since 0.29.0 the attacker's stop
(PAL hit-stop and world-trail freeze) starts once per action, on its first
impact message, sized as for one target; before, each hit batch restarted it,
so a three-target sweep froze the attacker about two ticks longer and ran its
trail backwards. Every struck entity still gets its own freeze and shudder.
Because the server restarts an attacker's revisions after a session reset, a
stop that resets the session, the local player's respawn or dimension change,
and a revision last seen more than 40 ticks ago all count as a new action.

### First-person arm (0.27.1)

`FirstPersonArmRenderer.onRenderHand` draws a skin and sleeve arm on
the same shoulder-pivot rig. It is registered before the trail, so the trail
blends over it, and it never cancels `RenderHandEvent`. It reads the same
displayed pose as the sword item, so a chain cross-fade moves the arm and the
sword together.

- Segments: upper arm, forearm, wrist, and a separate fist
  (`FirstPersonArmModel`, wide or slim, right or left, cut from the
  player skin; the fist carries the skin's hand rows).
- Fist: `FirstPersonArmIk` locks the fist to the grip frame. The handle
  crosses the palm at `rig.arm.grip_diagonal`, so the guard shows on the thumb
  side and the pommel below the little finger. The wrist sits one palm behind
  the handle.
- IK: a two-bone solve from an off-screen shoulder to the wrist. The elbow
  swivel comes from per-key `elbow`, and the hand's turn about the handle comes
  from per-key `grip_roll`. Both interpolate with the pose. If the wrist is out
  of reach, the shoulder moves instead. Wrist bend stays within the flexion,
  radial, and ulnar limits (`FLEX_LIMIT`, `RADIAL_LIMIT`, `ULNAR_LIMIT`).
- Size: the bone lengths, cross-section (`thickness`), grip diagonal, and
  follow-through gain live under `rig.arm` in `qingfeng_first_person.json`.
- Secondary motion: `FirstPersonArmLag` runs the grip's recent path (the rig
  sampled at earlier visual ticks) through an under-damped low-pass. The arm
  trails a cut and follows through past the stop. The lag moves the shoulder
  and swivels the elbow, and it is scaled back if it would break a wrist limit.
  It freezes with the hit-stop, fades out over a move's last ticks, and relaxes
  after an interrupted move. It is presentation only: the sword, trails, and
  gameplay ignore it.
- Idle: a slow breath moves the neutral hold and fades back in after a move.
- Off arm (0.29.0, optional): a rig with `rig.off_hand` also draws the other
  arm with its hand on the shaft at the contract's `off_hand_grip_center`,
  only while the off-hand slot is empty. Qingfeng's rig has no such block and
  draws as before. The block may give the off arm its own `thickness`
  (0.29.0-fix1), bone lengths (`upper_arm`, `forearm`), and released rest
  (`rest_direction`, `rest_reach`). Schema in [Lingxiao Spear](35_lingxiao_spear.md).
- Re-equip (0.29.0): a landed hit costs durability and the server resends the
  stack. On a plain `SwordItem` NeoForge then replayed the equip animation, so
  the weapon sank out of view for 5 to 6 ticks after every hit. Combat weapons
  are `CombatWeaponItem`, whose rule is vanilla's minus that replay: a stack
  that differs only in `minecraft:damage` keeps its place (also when switching
  to another copy of the same weapon), while a rename, an enchantment, another
  item, or another count re-equips. `slotChanged` is not consulted. Table in
  [Lingxiao Spear](35_lingxiao_spear.md).

The earlier rejected forms stay rejected: a separately damped complete arm left
the handle, and a pivot-locked complete arm floated mid-screen. The 0.27.0 form
(a forearm box that ran past the grip and swallowed the handle, with no wrist)
was the owner's "插入肉里的，非常僵硬".

### Qingfeng 3D model and geometry contract (0.27.1)

`tools/gen_qingfeng_sword_model.py` (stdlib only, deterministic; `--check`,
`--report`) is the only source of these files, which are never hand-edited:

- `models/item/qingfeng_sword_3d.json`: element model of a jian, with no parent
  and its own display transforms for every context except `gui`.
- `textures/item/qingfeng_sword_model.png`: its procedural 64x64 texture.
- `models/item/qingfeng_sword.json`: a `neoforge:separate_transforms` wrapper.
  `base` is the 3D model, and the `gui` perspective keeps the 2D
  `item/qingfeng_sword` sprite on `minecraft:item/handheld`.
- `combat/qingfeng_sword_geometry.json`: the contract (format 2), in model
  pixels, with the weapon along `+Y` (`axes.length`), flat normal X, and edge Z.
  It gives `grip_center`, the `handle`, `collar` (the guard), and `butt` (the
  pommel) extents, `head_base` and `head_tip` (the blade), and the axes.

Display transforms are derived from the old sprite's pose in each context. In
third person the grip centre lands on the fist centre. The first-person values
match the old sprite's vanilla hold outside cultivation mode; the rig undoes
them. The baked wrapper forwards `applyTransform` to the drawn model, so runtime
reads get the 3D values. `WeaponGeometry` loads the contract with the rig in
`FirstPersonSwingResources` on every reload. A missing or invalid contract
leaves Qingfeng on the vanilla hold. The first-person trail spans the
contract's optional `trail` (else `head_base` to `head_tip`). Since 0.29.0
the world trail is sized from the contract without clamps: its tip lies
`0.705` (shoulder to grip) plus the grip-to-trail-tip distance from the pivot
and its length is the trail span, both at the model's third-person display
scale. For Qingfeng that is 1.7 and 0.795 blocks, the same drawn trail as
before (`CombatWorldTrailsTest` pins every frame). Without a contract or scale
it falls back to 1.7 and 1.0.

### Third-person PAL poses (0.27.0)

`tools/gen_sword_pal_anims.py` (stdlib only, deterministic) generates
`player_animations/sword_combat.json` (and, since 0.29.0, one file per further
style from its own pose table and weapon). Its pose table is keyed by server tick and
phase (guard, anticipation, coil, contact, sweep, through, hold, recovery). It
solves the legs so both feet stay planted and checks every key with a
forward-kinematics copy of the PAL and vanilla transforms. Run
`python3 tools/gen_sword_pal_anims.py --check` after any edit; the focused
validator runs it too and fails on drift. Cut directions match the hitboxes:
横 left to right, 撩 right-low to left-high, 斜 left-high to right-low. The
lunge coils until the server step tick 6 and then lunges with the hips 6-7 px
forward (PAL body z is negative-forward). `CombatAnimationController`
cross-fades a chained START over 2 ticks and holds a stopped pose for 2 ticks
before the ready idle. Hit-stop runs through its `SpeedModifier`, and frozen
time is repaid at up to +0.5x speed. The same outermost modifier blends the legs
back to vanilla walking during the ready idle and the mode entry
(`LocomotionBlend`, from 0.27.1-fix1): PAL hands each bone in with the vanilla
pose, so the legs follow the vanilla limb-swing amount and the body root drops
its hip offset and turn, while the arms, torso, head, and sword keep the guard.
Moves are not blended. `tools/gen_blade_cut_sprite.py --check`
guards the procedurally generated 32x32 `textures/particle/blade_cut.png` the
same way.

PAL rotates `right_item` about the item origin, not the grip, so large item
rotations swung the handle out of the fist. Since 0.27.1 the generator writes a
per-key `right_item` position (`grip_compensation`) that moves the pivot to the
contract's grip centre. Its forward kinematics read the 3D model's third-person
display and the contract, and its self-checks bound the grip drift at keys and
between keys.

### Fallback swing and evidence

The original intercepted path canceled the mapped event and set its hand swing
false, so it also removed every visible first-person response. The client keeps
that event suppression but calls inherited
`LivingEntity#swing(InteractionHand.MAIN_HAND, false)` once for an eligible
unblocked prediction, or at an unpredicted authoritative buffered start. Unlike
the one-argument `LocalPlayer#swing`, this client-level overload does not send a
vanilla swing packet.

A developer capture at `960x540`, FOV 70 with mapped clicks showed all five
0.26.2 swings crossing the target during the hit, both trails, sweep/crit
particles, and hit-stop. Sound was not observable on the headless host. On
2026-09-30, after watching this revision (lab station A) next to Epic Fight, the
owner said A "现在不太行看上去" and asked for an optimized A: "我要的是那种战斗真实动作游戏的感觉".
The 0.26.2 swings were therefore not accepted, and 0.27.0 is the response.

Lab station E capture (2026-09-30, `/home/ubuntu/code/mc/combat-lab/out/E`,
local copy `out/preview/combat_lab/out/E`) of the 0.27.0 revision in a physical
client showed the first-person arm holding the sword, thin trails, blade_cut
particles, target slide, forward lunge displacement on move 5, camera roll on
heavy hits, and third-person full-body poses. This is implementation evidence
only, and every other 0.27.0 surface is `not_verified`. The owner's words on
0.27.0 and 0.27.1 are quoted in
[Combat Framework Comparison](33_combat_framework_comparison.md).

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
5. the move's `ReactionDefinition` slide/lift/lateral push along the frozen
   action facing, plus the current attack-knockback attribute and
   `EnchantmentHelper.modifyKnockback` in vanilla units, scaled by
   `1 - knockback resistance`. Vanilla hurt knockback is cancelled only for our
   own hit (`LivingKnockBackEvent`), and player targets get the vanilla motion
   packet. `EnchantmentHelper.doPostAttackEffectsWithItemSource` runs after
   successful damage;
6. one `hurtEnemy`/`postHurtEnemy` durability path for the whole action after
   its first successful target.

This retains the standard hurt pipeline, armor/protection, invulnerability,
PvP checks, NeoForge incoming/pre/post damage events, and the mapped
data-driven enchantment helpers. Cultivation mode deliberately excludes vanilla
sweeping, critical, sprint bonus, and cooldown scaling. Vanilla mode still uses
the untouched vanilla path.

## Timing And Hitbox Tuning

`data/myvillage/combat/style/basic_sword.json` is the only owner of the five
move contracts. Totals are
`11/13/15/17/20` ticks and active windows `3-4/4-6/5-7/6-8/7-9`, unchanged since
0.26.0. Since 0.27.0 the one-slot buffer opens at the active start
(`bufferStartTick` `3/4/5/6/7`), so a click during the hit is held. Clicks
during anticipation are still rejected, and a rejected click no longer counts
toward the two-tick minimum interval. A held click cancels the rest of the
recovery and starts the next move at `chainTick` `7/8/10/13/20`, through the same
stop-then-start path. Without a held click a move still plays to its total.
Move five cannot chain (`chainTick == totalTicks`), and the combo resets after
it. Combo timeout is 14 server ticks. `AttackMoveDefinition` enforces
`activeStartTick <= bufferStartTick < totalTicks` and
`activeEndTick < chainTick <= totalTicks`. The client mirrors this with
`ClientCombatState.bufferClick`/`chainDue` and predicts the chained move at the
chain tick. The server's COMPLETED stop and new START confirm it, and a missing
START drops it within 2-8 ticks.

The action faces the view yaw (`player.getYRot()`), not `yBodyRot`. Body and
head snap to it at start, and each chained move takes the current view yaw, so
the player can re-aim between hits. While a move runs, the temporary
`myvillage:combat_commit` speed modifier holds the player to 25% speed until
the hit ends, then to 60% until the chain tick. It is removed on every stop path,
and sprinting stops at start on the server and on the predicting client.

Every move steps: `StepDefinition(tick, maximumDistance, 0.35)` is
`(2, 0.30)`, `(3, 0.25)`, `(4, 0.30)`, `(5, 0.45)`, `(6, 1.40)`, with the
distance bound raised to `(0, 1.6]`. The step is a server-decided impulse, not a
server move. `CombatStepService` keeps the collision and support search, picks
the distance, and sets `player.setDeltaMovement(forward * distance *
GROUND_DRAG_COMPENSATION)` with `hurtMarked = true`. The client's own physics
then carries it out, like vanilla knockback. `GROUND_DRAG_COMPENSATION = 1 -
0.6 * 0.91` makes the ground slide cover the planned distance. Magnetism: if a
legal target lies within ±30° of facing and within range plus step, the step
stops `0.6` short of its hitbox edge. Light steps (0.5 or less) are skipped when
the target is already in range. Hit sweeps during and after a step use the
server-planned origin (start + forward × planned distance × progress), never the
client's echoed position. Only a step of at least `0.6` also sweeps the body
path.

Target reaction (`ReactionDefinition(hitstunTicks, slideDistance, lift,
lateralBias)`, `CombatReactionService`):
- Only `Mob` targets are frozen. They skip their server tick for the rounded
  hit-stop, and their slide impulse is held until the freeze ends.
- Mobs are then staggered for `9/9/10/13/16` ticks: no navigation, move, look,
  jump, or melee damage.
- Repeat stuns within 40 ticks scale by `0.7` each, and targets with 100+ max
  health take 30%. The Ender Dragon, Wither, and Warden are never frozen or
  stunned.
- Players are never frozen. They get the slide at once plus a 60% slow
  (`myvillage:combat_stun`).
- All reaction state clears on death, unload, dimension change, and server
  start/stop.
- Our hit clears the target's i-frames only for itself; afterwards the target
  keeps the larger of its old and new timer.

The five shape families are center thrust, a roughly 110-degree horizontal arc
swept left to right, a right-low to left-high rising diagonal, a thicker
left-high to right-low descending diagonal, and a long lunge thrust. Every
active sample uses `0.20` horizontal and `0.12` vertical tolerance. Broad-phase
union bounds are followed by segment/capsule-style narrow tests, wall clips,
legal-target filtering, deterministic contact-distance/entity-id ordering, an
action-wide target cap, and attempted-target deduplication. Payload protocol is
`7` since 0.28.0 (the impact payload carries the move id).

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
