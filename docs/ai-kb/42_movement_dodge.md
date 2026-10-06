# Movement Dodge (身法闪避)

0.40.0, the first runtime for 身法 (movement) techniques: a dodge key that
dashes the player with the highest-grade learned movement technique and opens
a short invulnerable window. Everything that matters is decided on the
server; the client sends the key press with its eight-way movement input and
draws the presentation. It is the first item of the technique system's
second phase ("手感", `docs/technique-system-brief.md` 3.3); the execution
brief with the defaults the owner may overturn is
`docs/movement-dodge-brief.md`. There is no capability spec for it; this
note and the code are the reference.

## Where each fact lives

| Fact | Source of truth |
|---|---|
| Distance, invulnerable ticks, `qi_cost`, cooldown per technique | `effects.movement` in `data/myvillage/myvillage/technique/<id>.json`, generated from the row's `effects` in `tools/technique_catalogue/catalogue.json` |
| Direction quantisation and world yaw | `combat/DodgeDirection` |
| Order of checks, window, cooldown, mastery, log lines | `combat/runtime/CombatDodgeService` |
| Recovery-only cancel rule | `combat/session/CombatSessionManager.canDodgeCancel` / `tryDodgeCancel` |
| Safe distance (collision and footing) | `combat/runtime/CombatStepService.safeDistance`, shared with action steps |
| Server sound and particles | `combat/runtime/CombatFeedbackService.dodge` |
| Key, intent throttle, receiving the start | `client/combat/ClientCombatKeyMappings.DODGE`, `ClientCombatEvents` |
| Afterimages, FOV surge, lean | `client/combat/CombatDodgeFx` |

## Contracts

- `CombatStopReason.DODGED`, appended last (the wire codec sends ordinals).
- `DodgeDirection`: `NONE` plus eight directions, each a `forward` and `left`
  sign. `fromInput(forwardImpulse, leftImpulse)` quantises vanilla's input
  with dead zone `DEAD_ZONE = 0.2`, strictly below vanilla's 0.3 sneaking
  input scale, so a sneaking player's held key still reads as a direction.
  `worldYaw(viewYaw)` is `viewYaw - atan2(left, forward)` in degrees (left of
  facing is `yaw - 90`); `NONE` is a back step.
- `CombatDodgeIntentPayload(DodgeDirection)`, serverbound
  `myvillage:combat_dodge_intent`: one byte of input and no authority.
  Timing, distance, protection, cooldown, and cost are the server's.
- `CombatDodgeStartPayload(entityId, startTick, directionYaw, distance,
  invulnerableTicks, durationTicks, cooldownTicks, techniqueId)`, clientbound
  `myvillage:combat_dodge_start`, sent with
  `PacketDistributor.sendToPlayersTrackingEntityAndSelf`. Presentation only:
  the motion arrives as the vanilla entity-motion packet of the server's
  impulse. The constructor rejects negative ids, ticks and cooldowns, a
  non-positive or non-finite distance, a non-finite yaw, and a duration
  shorter than `max(1, invulnerableTicks)`.
- `ModPayloads.PROTOCOL_VERSION` is `12` since 0.40.0 (was `11`).

## Server flow

`CombatDodgeService.handleIntent(player, direction)` on the server thread.
Each refusal logs one `rejected` line and changes nothing:

1. Preconditions, in this order: dead or removed, spectating, sleeping,
   using an item, riding, or meditating → `STATE`; combat mode not
   `CULTIVATION` → `MODE`; not on the ground → `AIRBORNE`. No weapon is
   required.
2. Technique: among the learned ids that resolve to a `MOVEMENT` technique
   with an `effects.movement` block, the highest grade; equal grades go to
   the smaller id (string order). Unknown ids, other categories and movement
   techniques without the block are skipped. None → `NO_TECHNIQUE`.
3. Cooldown: `now < readyTick` → `COOLDOWN`.
4. Timing: `canDodgeCancel` is true with no action running or once
   `actionTick > currentMove.activeEndTick` (the recovery). Wind-up and
   strike → `TIMING`; the press is not queued. Checked before the distance
   so a refused dodge never costs the player a running attack.
5. Distance: `yaw = wrapDegrees(direction.worldYaw(player.getYRot()))`,
   forward `(-sin yaw, 0, cos yaw)`, and
   `CombatStepService.safeDistance(player, forward, dash_distance, 1.0)`
   (support depth 1.0, as an action step). Zero → `BLOCKED`.
6. Only now `tryDodgeCancel` interrupts a running recovery with `DODGED`
   (`interrupt(player, DODGED, false)`: combo reset, no recovery lock).
7. Impulse: `setDeltaMovement(forward × impulseForDistance(distance))` with
   no vertical part, `hurtMarked = true`, `setSprinting(false)`.
8. State per UUID, all exclusive ends: invulnerable `[now, now +
   invulnerable_ticks)`, presentation `durationTicks = max(invulnerable_ticks,
   6)`, `readyTick = now + cooldown_ticks` (additions saturate).
9. Mastery: `masteryPoints + 1` on the technique (saturating) through
   `CultivationService.updateProfile`; a failed update logs a warning and the
   dodge stands.
10. Presentation: the start payload, `CombatFeedbackService.dodge` (8 `CLOUD`
    particles at the feet spread along the travel axis, `combat.sword.thrust`
    at volume 0.6, pitch 0.75 for everyone nearby), and the `started` line.

`qi_cost` is decoded with the rest of the block but not charged.

While the window is open:

- `onIncomingDamage` (a `LivingIncomingDamageEvent` listener) cancels damage
  to the `ServerPlayer` unless the source is in
  `DamageTypeTags.BYPASSES_INVULNERABILITY` (`/kill`, the void), and logs a
  `cancelled_damage` line.
- `CombatSessionManager.handleAttackIntent` refuses attack intents first
  thing (`sendRejection`), so an attack chained after a dodge starts once the
  window ends.

`tick(server)` drops states whose window, presentation and cooldown are all
over, and players no longer online. `CombatEvents` clears a player's state
on logout, death and dimension change and all state on server start and
stop.

## Log lines

INFO, `Locale.ROOT`, floats with two decimals; the capture tooling parses
them:

```text
DODGE_DEBUG player=<name> t=<tick> result=started technique=<id> dir=<DIR> yaw=<f> distance=<f> invuln=<n> cooldown=<n>
DODGE_DEBUG player=<name> t=<tick> result=rejected reason=<STATE|MODE|AIRBORNE|NO_TECHNIQUE|COOLDOWN|TIMING|BLOCKED>
DODGE_DEBUG player=<name> t=<tick> cancelled_damage=<amount> source=<damage type id>
```

`t` is the server game time, `dir` the `DodgeDirection` name as received,
`yaw` the world yaw travelled, `distance` the safe distance.

## Debug command

`/myvillage combat dodge status [player]` (permission 2; the source player
without an argument) prints one literal line:

```text
dodge <name>: technique=<id> grade=<g> distance=<d> invuln=<n> cooldown_ticks=<n> cooldown_remaining=<n> window_remaining=<n> dodging=<bool>
```

or `technique=none` when no learned technique qualifies. Learning for tests:
`/myvillage cultivation learn <target> myvillage:taxue_wuhen` (checks only
that the technique is registered, not realm or affinity), or
`/myvillage cultivation manual <target> <id>` to study the manual normally.

## Client

- Key `ClientCombatKeyMappings.DODGE`: `key.myvillage.dodge` (身法闪避 /
  Movement Dodge), default `GLFW_KEY_LEFT_ALT`, category
  `key.categories.myvillage`, rebindable.
- `ClientCombatEvents.onClientTick` drains its clicks. An intent is sent only
  with a living player, a level, no screen, client mode `CULTIVATION`, no
  meditation session, and at least `INTENT_INTERVAL_TICKS` (4)
  `ClientCombatClock` ticks after the previous one. The direction is
  `DodgeDirection.fromInput(player.input.forwardImpulse,
  player.input.leftImpulse)`. Nothing moves locally.
- On a start payload the server start tick is mapped to a local tick once;
  a dodge already over is ignored. `CombatDodgeFx.start` records it per
  entity id. For the local player `ClientCombatState.markLocalDodge` keeps
  the window end, `CombatCameraFx.stepSurge(6°)` widens the FOV, and
  `swingLean` leans by the sideways share of the travel × 1.2° (none for a
  straight forward or back dodge).
- Afterimages for every dodging player (`CombatDodgeFx.clientTick`): on the
  first drawn tick 4 `CLOUD` plus one `POOF`, then 2 `CLOUD` per tick through
  the first half of `durationTicks` and 1 after, 0.45 blocks behind the feet
  against the travel direction, drifting backwards. State is dropped when the
  dodge ends, the entity leaves or dies, or the client changes level
  (`onPlayerClone` also clears the local window).
- An attack click inside the local window is still sent (the server refuses
  it) but not predicted, so no local swing starts.
- A `DODGED` stop takes the existing stop path for non-`COMPLETED` reasons:
  animation and trails stop, the combo prediction returns to the first move,
  and ready idle is re-entered by the normal tick logic. `DODGED` is not one
  of the reasons that reset the server session's revisions.
- No dedicated dash pose: the animation controller treats a non-move id as a
  mode entry, so a pose needs its own lifecycle first and can only be judged
  on a real client.
- Timing reads only `ClientCombatClock`; nothing names an item id.

## Data and generator

| Technique | Grade | Element | `previous` | `dash_distance` | `invulnerable_ticks` | `qi_cost` | `cooldown_ticks` |
|---|---|---|---|---:|---:|---:|---:|
| `liuyun_bu` 流云步 | 1 黄 | water | none | 3.5 | 5 | 6 | 30 |
| `taxue_wuhen` 踏雪无痕 | 2 玄 | water | `liuyun_bu` | 4.5 | 7 | 10 | 24 |

Both have school null and the usual grade requirements (Qi Refining I; Qi
Refining IV with water affinity ≥ 1500 bp for 踏雪无痕) and study numbers.

- `tools/technique_catalogue/generator.py`: a catalogue row may carry an
  optional `effects` object keyed by effect kind; it is laid over the
  category default from `rules.json` per kind (row wins). A block of another
  kind than the row's category is refused, and a `movement` block must match
  Java's `TechniqueEffects.Movement`: all four fields, no others,
  `dash_distance` finite and > 0, the three others integers ≥ 0 within Java
  `int`. `--check` stays idempotent.
- `rules.json` classification: names containing 步, 身法, 无痕 or 遁 →
  `movement`, school null.
- Both techniques are in `world_sim/techniques.json` too, so the ledger's
  pool grew from 129 to 131 and sects, rogue cultivators and fortunes may
  draw a movement technique. Whether to exclude them is open.
- English names follow the generator: both language files carry the Chinese
  name.

## Capture

`python3 -m tools.combat_capture dodge [--beast ID] [--technique ID] [--out DIR]`
(defaults `myvillage:demon_wolf`, `myvillage:taxue_wuhen`,
`out/preview/movement_dodge/`): a survival player with no armour and
`naturalRegeneration` off, Qingfeng sword, cultivation combat mode, the
technique learned by command, the dodge pressed with xdotool (`Alt_L`; a left
dodge holds `a` first). At `/tick rate 5`, a fresh wolf forced into each move:

- bite (2.9 blocks ahead): no dodge; back steps at move ticks 4, 7, 9, 10;
  a left dodge at 9;
- pounce (6 blocks ahead): no dodge; back steps at 14, 17, 18, 19; a left
  dodge at 18;
- cooldown: two presses 5 ticks apart (the second should be `COOLDOWN`);
- recovery cancel: a press at action tick 3 of a sword swing (`TIMING`) and
  at action tick 6, two after the first move's last active tick (`started`).

Each trial records health before and after, distance moved, the
`DODGE_DEBUG` lines and the wolf's `BEAST_DEBUG ... hit minecraft:player ...
accepted=` lines; one F5-back video per wolf move. Output: `index.html`,
`manifest.json`, `video/`, `dodge_log.txt`. The results are developer
evidence, not owner acceptance; the README ledger "Movement Dodge (0.40.0)"
records them.

## Tests

- Java: `combat/DodgeDirectionTest` (the world-yaw table and the dead zone),
  `combat/runtime/CombatDodgeServiceTest` (preconditions order, technique
  choice, windows, cooldown, mastery, log lines),
  `combat/session/CombatDodgeCancelTest` (the recovery-only rule, combo reset),
  `combat/network/CombatPayloadTest` (both payloads round trip, bounds
  rejected, protocol `12`), `client/combat/CombatDodgeFxTest` (afterimage
  rhythm and direction, remaining ticks, lean, intent throttle, the local
  window suppressing prediction, a `DODGED` stop keeping the server session
  and restarting the combo).
- Python: `tools/tests/test_gen_technique_catalogue.py` `RowEffectsTest`,
  `tools/tests/test_combat_capture_dodge.py` (the capture's pure helpers).

## Not implemented

- Qi: `qi_cost` is not charged; there is no qi pool (maximum, regeneration,
  HUD bar) yet.
- A dash pose, a HUD cooldown readout, mastery tiers, and a chosen-technique
  slot (the highest grade is always used).
- Owner decisions still open: the Left Alt default, whether to charge qi,
  the feel of 5/7 invulnerable ticks, 30/24 cooldown ticks and 3.5/4.5
  blocks, a dedicated pose, and whether movement techniques stay in the
  ledger's pool.

## See also

- [41_technique_system.md](41_technique_system.md) (catalogue, registries, manuals), [34_combat_data_and_capture.md](34_combat_data_and_capture.md) (combat runtime, steps, capture), [38_hostile_beasts.md](38_hostile_beasts.md) (the wolf's moves the trials dodge), [28_cultivation_core.md](28_cultivation_core.md) (profile and mastery)
- Briefs: `docs/movement-dodge-brief.md`, `docs/technique-system-brief.md`
- [combat-style-data](../../openspec/specs/combat-style-data/spec.md), [combat-capture-tooling](../../openspec/specs/combat-capture-tooling/spec.md), [hostile-beast-runtime](../../openspec/specs/hostile-beast-runtime/spec.md), [cultivation-definition-registries](../../openspec/specs/cultivation-definition-registries/spec.md)
- Knowledge-base index: [INDEX.md](INDEX.md)
