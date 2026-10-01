## ADDED Requirements

### Requirement: Qingfeng Sword is a complete independent diamond-tier sword
The mod SHALL register `myvillage:qingfeng_sword` as a `SwordItem` using mapped `Tiers.DIAMOND` and `SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)`. It SHALL therefore have durability `1561`, held attack damage `7.0`, held attack speed `1.6`, diamond enchantment value and repair ingredient behavior, normal durability/Mending compatibility, and membership in the vanilla sword item tag. It SHALL appear in `myvillage:main` with English `Qingfeng Sword`, Chinese `青锋剑`, an original 2D pixel texture as its inventory icon, a generated 3D held model (see the held-model requirement), and a shaped diamond-sword-equivalent recipe.

#### Scenario: The sword is obtained
- **WHEN** a player opens `myvillage:main` or runs `/give @s myvillage:qingfeng_sword`
- **THEN** the registered localized sword SHALL have a resolved model and texture

#### Scenario: The sword attributes are inspected
- **WHEN** the Qingfeng Sword is held in the main hand
- **THEN** its tier, durability, attack-damage modifier, attack-speed modifier, enchantment value, and diamond repair ingredient SHALL match the mapped vanilla diamond sword

#### Scenario: The sword is enchanted or repaired
- **WHEN** normal sword-compatible enchantment, anvil repair, Mending, or durability loss logic evaluates the stack
- **THEN** the Qingfeng Sword SHALL participate through ordinary sword/tier/item hooks

### Requirement: Qingfeng Sword has a generated 3D held model and geometry contract
The Qingfeng item model SHALL be a `neoforge:separate_transforms` model whose `base` is the element model `myvillage:item/qingfeng_sword_3d` and whose `gui` perspective keeps the original 2D `myvillage:item/qingfeng_sword` sprite on `minecraft:item/handheld`. The 3D model SHALL depict a straight double-edged jian (ridged blade with straight edges and a tapered tip, guard, wrapped grip, pommel) with its own texture `myvillage:item/qingfeng_sword_model`, SHALL have no parent that ends in `builtin/generated`, and SHALL carry its own hand, ground, fixed, and head display transforms but no `gui` transform.

`tools/gen_qingfeng_sword_model.py` SHALL be the only source of the 3D model, its texture, the wrapper model, and the geometry contract `assets/myvillage/combat/qingfeng_sword_geometry.json`. It SHALL be deterministic, standard-library only, and provide `--check` (fail on drift from the committed outputs or on a failed self-check) and `--report`. The contract SHALL state, in model pixels with the blade along `+Y`, the flat normal along X, and the edge along Z: the grip centre, the handle, guard, and pommel extents, the blade base and tip, and the axes. Its display transforms SHALL be derived so the third-person grip centre lands in the fist and the blade keeps the direction the PAL moves were designed for. The generated third-person PAL animation SHALL compensate `right_item` rotation so the grip centre stays in the fist during moves.

The first-person grip and first-person trail SHALL read the blade base and tip from the contract, and the world trail's drawn blade length SHALL follow the contract blade at the model's third-person display scale, with a bounded fallback when the contract is absent. The model and contract are presentation only and SHALL NOT change reach, hit shapes, or damage.

#### Scenario: The sword is seen in the inventory and in hand
- **WHEN** a player views the Qingfeng Sword in a GUI slot and then holds it
- **THEN** the slot SHALL show the 2D icon and the hand SHALL show the 3D jian without missing textures

#### Scenario: A generated sword asset is hand-edited
- **WHEN** the 3D model, its texture, the wrapper model, or the geometry contract differs from the generator output
- **THEN** `python3 tools/gen_qingfeng_sword_model.py --check` SHALL fail and the focused validator SHALL report generator drift

#### Scenario: The sword model is regenerated with new proportions
- **WHEN** the generator changes the blade length or grip position and resources reload
- **THEN** the first-person grip, first-person trail, and world-trail blade length SHALL follow the new contract without a Java change

### Requirement: Qingfeng Sword and rideable flying sword remain separate
`myvillage:qingfeng_sword` and `myvillage:rideable_flying_sword` SHALL retain distinct item ids, Java item types, models, textures, interactions, contracts, and gameplay state. The Qingfeng Sword SHALL NOT summon, mount, recall, render, or control the rideable-flying-sword entity.

#### Scenario: Either item is used
- **WHEN** a player attacks or uses one of the two sword items
- **THEN** only that item's declared combat or vehicle behavior SHALL run
- **AND** no item or entity instance SHALL be substituted for the other

### Requirement: Combat preference is independent persistent server state
The server SHALL store one immutable `CombatPreference` attachment per player containing only `combat_mode` with codec values `vanilla` and `cultivation`. The default SHALL be `vanilla`; the attachment SHALL serialize, copy on death, and survive save/restart. It SHALL not be a field in `CultivationProfile` or a client-only configuration value.

#### Scenario: A new player joins
- **WHEN** no serialized combat preference exists
- **THEN** the server SHALL install and synchronize `vanilla`

#### Scenario: A cultivation preference persists
- **WHEN** a player selects cultivation mode, saves, restarts, dies, respawns, or changes dimension
- **THEN** the authoritative preference SHALL remain cultivation and the owning client SHALL receive the current snapshot

#### Scenario: Cultivation profile is inspected
- **WHEN** its codec and fields are enumerated after combat integration
- **THEN** no combat mode, combo index, action tick, hit set, or attack revision SHALL be present

### Requirement: Mode switching uses a configurable bounded intent
The physical client SHALL register `Switch Combat Mode` as a `KeyMapping` defaulted to `R`; players MAY rebind it through normal controls. One key activation SHALL send only an empty toggle intent. The server SHALL rate-limit the intent, derive the opposite of its stored mode, replace the preference through `CombatService`, synchronize the owning client, and display a translatable action-bar result. The client SHALL NOT submit an enum value as authority.

#### Scenario: R is pressed once with no GUI
- **WHEN** a live player activates the configured mode key once outside a screen
- **THEN** at most one toggle intent SHALL be sent and the server SHALL decide the resulting mode

#### Scenario: The key is rebound
- **WHEN** the player changes the key binding and activates the new binding
- **THEN** mode switching SHALL still work without a physical `R` check in event logic

#### Scenario: Toggle packets are spammed
- **WHEN** the server receives toggle intents faster than the declared minimum interval
- **THEN** excess intents SHALL be ignored without changing preference revision or bypassing recovery

### Requirement: Vanilla mode remains fully vanilla
While the stored mode is `vanilla`, MyVillage SHALL NOT cancel the attack key, suppress hand swing, replace attack packets, run a combat session, or play a PAL attack for the Qingfeng Sword. Vanilla left-click attack, cooldown, block mining, enchantment/damage events, critical/sweep behavior, and durability SHALL remain on the normal Minecraft/NeoForge path.

#### Scenario: Qingfeng Sword attacks in vanilla mode
- **WHEN** the player left-clicks an entity with the Qingfeng Sword in vanilla mode
- **THEN** the normal vanilla attack input and `Player#attack` path SHALL execute without a MyVillage attack intent

#### Scenario: Qingfeng Sword hits a block in vanilla mode
- **WHEN** the player holds attack on a block with the Qingfeng Sword in vanilla mode
- **THEN** normal sword block interaction/mining behavior SHALL remain available

### Requirement: Cultivation interception is exact and remap-safe
The client SHALL listen to cancelable `InputEvent.InteractionKeyMappingTriggered` and act only when `isAttack()` is true, no GUI is open, the local player is alive, the authoritative cached mode is cultivation, and the main hand is exactly `myvillage:qingfeng_sword`. It SHALL cancel further vanilla processing, set the event hand swing false, and send one attack intent. For visible first-person feedback it SHALL start the Qingfeng-only item-transform sequence from the predicted move and correct it from the authoritative start; it MAY also start exactly one packet-free local fallback swing through the two-argument inherited swing overload. It SHALL NOT call the one-argument `LocalPlayer#swing`, emit a vanilla swing packet, restore vanilla attack processing, or test a physical mouse button constant.

#### Scenario: Cultivation sword attacks an entity
- **WHEN** the remapped attack action is triggered with the Qingfeng Sword in cultivation mode
- **THEN** vanilla attack processing and the event-generated vanilla hand swing SHALL be canceled
- **AND** exactly one bounded sword-attack intent SHALL be attempted
- **AND** the predicted Qingfeng first-person sequence SHALL be corrected or stopped by the authoritative action broadcast
- **AND** at most one packet-free local fallback swing MAY accompany the accepted local prediction

#### Scenario: Cultivation sword points at a block
- **WHEN** the attack action is triggered against a block with the Qingfeng Sword in cultivation mode
- **THEN** vanilla sword mining SHALL not begin
- **AND** the same bounded combat intent path SHALL run

#### Scenario: Cultivation player switches to a pickaxe
- **WHEN** the attack action is triggered with a pickaxe or another unsupported item while the stored mode remains cultivation
- **THEN** MyVillage SHALL not cancel, swing-suppress, or replace the normal action

#### Scenario: A screen is open or the player is dead
- **WHEN** the mapped attack action fires in either state
- **THEN** no combat intent or local prediction SHALL be produced

### Requirement: Combat client payloads are intent-only
`CombatModeTogglePayload` and `SwordAttackIntentPayload` SHALL contain no mode value, combo index, move id, target entity, hit result, damage, hitbox, position, yaw, velocity, movement endpoint, animation completion, or client tick authority. The server SHALL derive the sender and all authoritative values. The payload protocol version SHALL advance whenever a combat payload shape changes (currently `6`) without changing the existing flying-sword bitset or cultivation snapshot semantics.

#### Scenario: Payload codecs are inspected
- **WHEN** every new C2S combat payload field is enumerated
- **THEN** only the payload type itself SHALL represent the toggle or attack action intent

#### Scenario: A client attempts to choose move five
- **WHEN** a malformed or alternate packet includes a combo index or move id
- **THEN** no registered combat codec SHALL decode such authority

#### Scenario: Existing payloads register after the protocol update
- **WHEN** the single `ModPayloads` registrar initializes
- **THEN** flying-sword input, cultivation snapshots/time/status/intents, and new combat payloads SHALL retain their declared directions and one handler each

### Requirement: The server revalidates every attack intent
Before accepting an attack, the server SHALL verify that the sender is alive, non-removed, non-spectator, in cultivation mode, holding the Qingfeng Sword in the main hand, in the same current world/dimension as the session, not mounted, not meditating, not advancing, not sleeping, not using an item, and not in another declared attack-forbidden state. It SHALL validate server game tick, packet rate, recovery lock, and the current `CombatSession` transition. Rejection SHALL not damage, move, or select a target.

#### Scenario: A legal idle intent arrives
- **WHEN** all server checks pass and no recovery lock is active
- **THEN** the server SHALL select the state-machine move, create one authoritative revision, and broadcast its start

#### Scenario: A spoofed or ineligible intent arrives
- **WHEN** one liveness, mode, item, world, cultivation, mount, rate, or timing check fails
- **THEN** the server SHALL reject it without starting a move, changing combo index, moving the player, or damaging an entity

### Requirement: Combo state is transient and recovery-safe
The server SHALL keep current move, ordered combo position, action start game tick, active-window state, one buffered-input bit, already-hit entity ids, revision, current weapon id, and stop reason only in runtime memory. It SHALL clear current action/combo state on death, logout, dimension change, weapon change, switch to vanilla mode, mount, meditation/advancement start, or another disallowed state. Clearing an unfinished action for a mode/item switch SHALL retain a server recovery lock until the original action end tick.

#### Scenario: The server restarts
- **WHEN** a combat action or partial combo existed before shutdown
- **THEN** the next process SHALL have no restored action, combo index, hit set, or recovery session
- **AND** only the persisted mode preference SHALL remain

#### Scenario: A player swaps items during recovery
- **WHEN** the unfinished action is cleared and the player immediately swaps back and attacks
- **THEN** the server SHALL reject the new action until the original recovery lock expires

#### Scenario: A player toggles modes during recovery
- **WHEN** the action is canceled by switching to vanilla and the player immediately switches back
- **THEN** the original recovery lock SHALL still prevent an immediate cultivation attack

### Requirement: Combo progression is server-owned and one input advances one move
The first accepted idle intent SHALL start `basic_sword_01_thrust`. One later legal intent in sequence SHALL start moves two, three, four, and five respectively. The client SHALL never choose the index. A missed move SHALL still be eligible to continue. Move five completion and combo timeout SHALL reset the next move to one.

#### Scenario: Five legal clicks continue in time
- **WHEN** one intent is accepted for each move within its declared continuation windows
- **THEN** the server SHALL start move ids one through five exactly once in order

#### Scenario: Every move misses
- **WHEN** no target is hit but continuation inputs remain legal
- **THEN** combo progression SHALL still reach move five

#### Scenario: The player pauses past timeout
- **WHEN** no continuation intent is accepted before the server combo deadline
- **THEN** the next legal attack SHALL start move one

#### Scenario: Move five ends
- **WHEN** the fifth action completes with or without a hit
- **THEN** the next legal attack SHALL start move one

### Requirement: Input buffering has capacity one and cannot skip timing
Each move SHALL declare a `bufferStartTick` with `activeStartTick <= bufferStartTick < totalTicks`; from that tick until the move ends the session SHALL accept one next-input buffer, so a click during the hit is held. A second buffered input SHALL be rejected and SHALL not replace, stack, or skip a move. Input during anticipation (before `bufferStartTick`) and over-rate input SHALL be rejected without extending the action or advancing the combo, and a rejected intent SHALL not count toward the minimum intent interval. State progression SHALL use server game ticks and remain correct when real-time TPS drops.

#### Scenario: One input is buffered during the hit
- **WHEN** an intent arrives at or after the current move's `bufferStartTick` and no input is buffered
- **THEN** exactly one next move SHALL start at the declared server transition (the move's `chainTick`)

#### Scenario: The player clicks repeatedly in the same buffer window
- **WHEN** two or more intents arrive before the buffered move starts
- **THEN** only the first SHALL be retained and the combo SHALL advance by one

#### Scenario: Input arrives before the buffer window
- **WHEN** an intent arrives during anticipation, before the move's `bufferStartTick`
- **THEN** it SHALL not end the move, skip recovery, or advance the combo

#### Scenario: Server TPS is low
- **WHEN** wall-clock time per server tick increases
- **THEN** move duration, active frames, buffer window, and timeout SHALL still advance only by server game tick

### Requirement: Five move definitions are centralized and exact
One `BasicSwordStyle` definition graph SHALL own the following initial contract and SHALL be the only authority used by session, hit, animation, validator, and debug code:

| Move id | Display name | Total ticks | Active ticks | Damage multiplier | Maximum targets | Range |
|---|---|---:|---:|---:|---:|---:|
| `basic_sword_01_thrust` | 一式：青锋问路 | 11 | 3-4 | 0.90 | 1 | 3.0 |
| `basic_sword_02_horizontal_cut` | 二式：流云横渡 | 13 | 4-6 | 0.95 | 3 | 2.8 |
| `basic_sword_03_rising_cut` | 三式：燕返撩月 | 15 | 5-7 | 1.00 | 2 | 2.8 |
| `basic_sword_04_diagonal_cut` | 四式：回风落雁 | 17 | 6-8 | 1.10 | 3 | 3.0 |
| `basic_sword_05_lunge_thrust` | 五式：一线穿云 | 20 | 7-9 | 1.25 | 2 | 3.5 |

#### Scenario: Runtime values are searched
- **WHEN** event handlers, payload handlers, and hit resolvers are inspected
- **THEN** they SHALL resolve timing, multiplier, target count, range, animation, hit shape, and step from the centralized definition rather than duplicate literals

#### Scenario: Definition and animation ids are compared
- **WHEN** focused validation runs
- **THEN** every move id SHALL map one-to-one to the same PAL animation id and compatible duration

### Requirement: A held click chains into the next move at a per-move chain tick
Each move SHALL declare a `chainTick` with `activeEndTick < chainTick <= totalTicks` and `chainTick >= bufferStartTick`. When the session holds a buffered click and the current move reaches its `chainTick`, the server SHALL complete the current move and start the next move in the same tick through the ordinary stop-then-start path, cancelling the remaining recovery. Without a buffered click a move SHALL play to `totalTicks`. The shipped values SHALL be:

| Move id | Buffer start | Chain tick | Step (tick, max blocks) | Hitstun | Slide | Lift | Lateral bias |
|---|---:|---:|---|---:|---:|---:|---:|
| `basic_sword_01_thrust` | 3 | 7 | 2, 0.30 | 9 | 0.30 | 0.00 | 0.0 |
| `basic_sword_02_horizontal_cut` | 4 | 8 | 3, 0.25 | 9 | 0.35 | 0.00 | 0.3 |
| `basic_sword_03_rising_cut` | 5 | 10 | 4, 0.30 | 10 | 0.30 | 0.20 | 0.0 |
| `basic_sword_04_diagonal_cut` | 6 | 13 | 5, 0.45 | 13 | 0.60 | 0.00 | 0.0 |
| `basic_sword_05_lunge_thrust` | 7 | 20 | 6, 1.40 | 16 | 2.00 | 0.25 | 0.0 |

Move five SHALL NOT chain (`chainTick == totalTicks`) and the combo SHALL reset after it. The action facing SHALL be the attacker's server view yaw (`getYRot`) when each move starts; body and head yaw SHALL snap to it, and a chained move SHALL take the view yaw current at its start. While a move runs the server MAY apply a temporary movement-speed modifier for commitment (strike weight until the active window ends, lighter until `chainTick`), SHALL stop sprinting at start, and SHALL remove the modifier on every stop path.

#### Scenario: A click during the hit chains
- **WHEN** a click arrives during move one's active window and move one reaches tick `7`
- **THEN** the server SHALL complete move one and start move two in that tick with a new revision

#### Scenario: No click is held
- **WHEN** no buffered click exists when a move reaches its `chainTick`
- **THEN** the move SHALL continue its recovery until `totalTicks`

#### Scenario: The finisher is clicked through
- **WHEN** a click is held during move five
- **THEN** move five SHALL play to tick `20` and the next move SHALL be move one

#### Scenario: The player re-aims between chained moves
- **WHEN** the view yaw changes before a chained move starts
- **THEN** the new move's facing SHALL be the view yaw at its start and SHALL stay frozen for that action

### Requirement: Hit detection uses broad and narrow phases
At each active server tick, `CombatHitResolver` SHALL derive a broad union AABB and then run move-specific OBB, capsule, or swept-volume narrow-phase tests from server position, the action's frozen facing yaw (the attacker's server view yaw at action start), current action tick, and local definition samples. It SHALL NOT represent all five moves as one fixed inflated AABB. Visual tolerance SHALL be bounded to at most `0.25` horizontally and `0.15` vertically.

#### Scenario: A broad-phase candidate misses the narrow shape
- **WHEN** an entity lies inside the union AABB but outside every move sample plus tolerance
- **THEN** it SHALL not be hit

#### Scenario: The player turns after the action starts
- **WHEN** an active tick resolves samples
- **THEN** the resolver SHALL use the action's declared server-authoritative facing policy and SHALL not accept a client-provided yaw or hitbox

### Requirement: Each move has a distinct geometric vocabulary
Move one SHALL use a narrow long center thrust and avoid easy side/rear hits. Move two SHALL sweep left-to-right horizontally (from the attacker's left to right, a backhand) through approximately 100-120 degrees. Move three SHALL sample a right-low to left-high rising diagonal and apply no prolonged launch. Move four SHALL sample a thicker left-high to right-low descending diagonal that is narrower than move two and uses its higher multiplier. Move five SHALL combine a long thrust with the server-planned lunge sweep. First-person swings, third-person poses, trails, and particles SHALL follow these same directions.

#### Scenario: Side targets surround move one
- **WHEN** one target is on the center line and another is equally near at the side or rear
- **THEN** the center target MAY be hit while the side/rear target SHALL fail the narrow thrust

#### Scenario: Move two crosses several legal targets
- **WHEN** candidates lie along its sampled horizontal arc
- **THEN** at most three SHALL be selected in deterministic order

#### Scenario: Move three succeeds
- **WHEN** a target intersects its rising diagonal samples
- **THEN** damage and only the move's declared reaction slide and small lift SHALL apply
- **AND** no long-duration floating state SHALL be created

#### Scenario: Move four and move two are compared
- **WHEN** their narrow-phase envelopes are measured
- **THEN** move four SHALL be thicker along its diagonal but have a narrower horizontal sweep than move two

### Requirement: Move steps are bounded server-decided impulses
Every move MAY declare one `StepDefinition(actionTick, maximumDistance, supportDepth)` with `0 < maximumDistance <= 1.6`; the shipped steps are listed in the chain-window requirement, with move five's lunge at most `1.40` blocks. At the step tick the server SHALL choose the distance: it SHALL search forward along the frozen facing for collision-free player space with supporting collision within the support depth, and it SHALL apply magnetism: when a legal target lies within ±30° of facing and within range plus step, the step SHALL stop `0.6` short of that target's hitbox edge (never negative), and a light step (at most `0.5`) SHALL be skipped when the target is already within range. The server SHALL then execute the step as a motion impulse (`setDeltaMovement` along the facing, scaled by the ground-drag compensation `1 - 0.6 * 0.91`, plus `hurtMarked`) that the attacker's client physics carries out with normal collision, like vanilla knockback. It SHALL NOT teleport or `move` the player on the server, and SHALL NOT read a client coordinate, velocity, or endpoint. Hit sweeps during and after the step SHALL use the server-planned origin (start plus facing times planned distance times step progress), so hits never depend on the client's echoed position. A step of at least `0.6` SHALL also sweep the planned body path.

#### Scenario: Open supported ground is ahead
- **WHEN** move five reaches its declared step tick with no target in its magnetism cone
- **THEN** the server MAY impel the player forward by at most `1.40` block and SHALL test the planned swept volume

#### Scenario: A solid wall is ahead
- **WHEN** the intended step or player box intersects the wall
- **THEN** the chosen distance SHALL stop before collision and the hit sweep SHALL not extend through the wall

#### Scenario: A cliff edge is ahead
- **WHEN** the destination lacks declared supporting collision within the safety depth
- **THEN** the forward step SHALL be suppressed or shortened to supported ground

#### Scenario: A target stands in front
- **WHEN** a legal target is within ±30° of facing and within range plus step
- **THEN** the step SHALL end `0.6` short of the target's hitbox edge instead of running through it

#### Scenario: A target lies between start and end of the lunge
- **WHEN** the planned lunge path crosses its bounding box during active frames
- **THEN** the swept volume SHALL detect it even if neither endpoint overlaps it

### Requirement: Target selection is legal deterministic and deduplicated
The server SHALL consider only attackable same-world entities that are alive, not removed, not spectator, not invalidly invulnerable, not the attacker, and permitted by PvP/team/friendly rules. A solid-block clip between the attack origin and target SHALL reject wall-through hits. Candidates SHALL order by first contact distance then entity id, stop at the move maximum, and record a successful or attempted target so the same action cannot damage it twice.

#### Scenario: One target remains in the hitbox for several active ticks
- **WHEN** the target was already processed by the current action
- **THEN** later active ticks SHALL not apply another damage sequence to it

#### Scenario: A target is behind a full wall
- **WHEN** its bounding box geometrically intersects an extended sample but the solid-block clip is blocked
- **THEN** it SHALL not be selected or hurt

#### Scenario: More candidates than the maximum intersect
- **WHEN** deterministic ordering contains additional legal candidates
- **THEN** only the first declared maximum SHALL be processed

#### Scenario: Friendly fire is disabled
- **WHEN** a same-team or PvP-protected player intersects a move
- **THEN** ordinary server PvP/team rules SHALL prevent damage

### Requirement: Cultivation damage uses normal damage events without vanilla duplicate or sweep
For each selected target the server SHALL fire the NeoForge player-attack gate, use `playerAttack` damage source, derive base damage from current `Attributes.ATTACK_DAMAGE`, apply the move multiplier and current item target bonus, invoke `EnchantmentHelper.modifyDamage`, and call ordinary `Entity#hurt`. A successful hit SHALL replace vanilla hurt knockback for that hit only with the move's reaction impulse (slide, lift, and lateral bias along the frozen facing) plus current attack-knockback attribute and `EnchantmentHelper.modifyKnockback` contributions, scaled by `1 - knockback resistance`, and SHALL run post-attack enchantment effects. The service SHALL clear the target's invulnerability timer immediately before its own `hurt` call, so chained moves are not swallowed by vanilla invulnerability frames, and SHALL restore the larger of the previous and new timer afterwards, so other damage sources still see vanilla invulnerability. It SHALL not call `ServerPlayer#attack` for the same move, create a second vanilla hit, or trigger vanilla sweeping, critical, or sprint-attack behavior.

#### Scenario: A normal target is hit
- **WHEN** no attack or incoming-damage event cancels the action
- **THEN** armor, protection, NeoForge incoming/pre/post damage events, and the declared move damage SHALL apply through the standard hurt sequence
- **AND** the target's invulnerability timer after the hit SHALL be at least what it was before

#### Scenario: An attack event cancels one target
- **WHEN** `AttackEntityEvent` or the held item's left-click hook cancels that target
- **THEN** no damage, knockback, post-attack effect, or durability charge SHALL be attributed to that target

#### Scenario: The sword has compatible enchantments
- **WHEN** damage, knockback, or post-attack enchantment helpers evaluate the current Qingfeng stack
- **THEN** applicable Sharpness/Smite/Bane-style damage, Knockback, Fire Aspect, and other data-driven post-attack effects SHALL participate through mapped helpers

#### Scenario: A cultivation move hits
- **WHEN** its custom damage path succeeds
- **THEN** no vanilla sweep target, duplicate original-input damage, cultivation critical, or sprint bonus SHALL also occur

### Requirement: Weapon durability is charged once per successful action
An action that damages at least one legal target SHALL run ordinary sword `hurtEnemy`/`postHurtEnemy` durability behavior exactly once after its first successful target. An action with no successful damage SHALL not consume attack durability. Multi-target moves SHALL not charge once per target.

#### Scenario: A three-target move succeeds
- **WHEN** one action damages three targets
- **THEN** the Qingfeng Sword SHALL lose one ordinary sword attack durability unit, subject to normal Unbreaking/Mending behavior

#### Scenario: Every target rejects damage
- **WHEN** invulnerability, PvP, or events prevent every hurt call
- **THEN** the action SHALL not consume attack durability

### Requirement: Start and stop synchronization is revisioned and tracking-aware
The server SHALL broadcast accepted action starts to the attacker and tracking players with attacker entity id, move id, server start tick, monotonically increasing revision, and the action's frozen server facing yaw (the attacker's view yaw at action start). It SHALL broadcast authoritative stops with attacker id, revision, and a bounded reason when an active action is canceled or corrected. Client code SHALL animate remote players only from those broadcasts.

#### Scenario: Two players observe one attacker
- **WHEN** the server accepts the attacker's move
- **THEN** the attacker and both tracking clients SHALL receive the same move id, start tick, and revision

#### Scenario: Weapon changes mid-action
- **WHEN** the server aborts the current action
- **THEN** a matching or newer stop revision SHALL make tracking clients restore that player's normal pose

### Requirement: Local animation prediction is disposable
The local client MAY immediately predict an animation from its last authoritative read-only combo state, but it SHALL send only the empty intent and SHALL not install a server action. The next start payload SHALL correct move id/timing/revision; rejection or stop SHALL remove the prediction.

#### Scenario: Prediction matches
- **WHEN** the server accepts the predicted next move
- **THEN** the client SHALL align it to server start time without sending completion or hit data

#### Scenario: Prediction is wrong
- **WHEN** the server selects another move or rejects the intent
- **THEN** the client SHALL replace or stop the predicted animation and SHALL not retain predicted combo authority

#### Scenario: A chained move is predicted
- **WHEN** the local action holds a buffered click and reaches its `chainTick` on the client
- **THEN** the client MAY start the next move locally and stop sprinting locally
- **AND** the server's completion stop and new start SHALL confirm it, while a missing start within a bounded timeout SHALL drop the prediction

### Requirement: Combat feedback is presentation-only and follows server outcomes
Each move SHALL declare presentation cues beside its definition: swing sound family and pitch, a heavy-hit flag (moves four and five), hit-stop ticks (`1.5/2/2/3/4`), camera trauma, and a blade-cut roll. Swing, thrust, hit, heavy-hit, and heavy-impact (`combat.sword.impact_heavy`) sounds SHALL be MyVillage sound events whose `sounds.json` entries currently alias vanilla sounds and carry bilingual subtitles, so original audio can replace them without code changes. One tick before a move's server active start (`activeStartTick - 1`) the server SHALL play the swing sound, with small pitch variation, for nearby players except the attacker; the attacker's client SHALL play the same cue locally about one tick ahead of its visible strike. After damage succeeds, and only for targets that took it, the server SHALL play the hit sound at the contact (plus the heavy-impact layer on heavy moves), spawn one `myvillage:blade_cut` particle and a small bounded number of crit sparks at the true contact point, and send the attacker one clientbound hit confirmation containing only attacker id, action revision, and hit count. It SHALL NOT spawn the vanilla sweep-attack particle.

The attacker's client MAY answer a hit confirmation with one hit-stop per action lasting the move's hit-stop ticks: the first-person visual clock freezes for the first 60% of the stop, creeps for the rest, then runs slightly faster so the swing still ends exactly at the server total. The stop SHALL start no earlier than the rig's per-move contact tick and SHALL be skipped when too little of the action remains.

The first-person client SHALL draw a thin alpha-blended (not additive) 剑光 ribbon swept by the Qingfeng blade during each move's strike window by re-posing the blade at earlier visual ticks, fading after the window. Other clients, and the attacker in a detached camera, SHALL draw a world-space ribbon along the move's own server hitbox samples, positioned by the broadcast facing yaw. Trails, sounds, particles, camera effects, and hit-stop SHALL NOT change timing, hit selection, damage, movement, or any payload the client sends, and SHALL NOT constitute a sword projectile.

#### Scenario: A cut lands
- **WHEN** the server applies damage from a horizontal cut
- **THEN** nearby players SHALL hear the hit sound and see a blade-cut particle and crit sparks at the contact, and no sweep particle
- **AND** the attacker SHALL receive a hit confirmation and MAY see a hit-stop of the move's declared length that still ends on the server total

#### Scenario: A swing misses
- **WHEN** a move's active window resolves no damage
- **THEN** the swing sound and trail SHALL still play
- **AND** no hit sound, hit particle, hit confirmation, impact broadcast, or hit-stop SHALL occur

#### Scenario: A remote player attacks
- **WHEN** a tracking client receives an attack start
- **THEN** it SHALL draw that player's trail from the move's hitbox samples and broadcast facing yaw
- **AND** it SHALL remove the trail when an interrupting stop arrives

### Requirement: Struck targets react through server-owned hitstun and slide
Each move SHALL declare a `ReactionDefinition(hitstunTicks, slideDistance, lift, lateralBias)`. After a successful hit the server SHALL apply the reaction:
- A struck `Mob` SHALL be frozen (its server tick skipped) for the move's rounded hit-stop, with its slide impulse held until the freeze ends.
- The mob SHALL then be staggered for the move's hitstun: no navigation, movement, look, or jump control, and no melee damage from it.
- Each repeated stun within 40 ticks SHALL scale by `0.7`, and targets with at least `100` max health SHALL take 30% of the hitstun.
- The Ender Dragon, Wither, and Warden SHALL never be frozen or stunned.
- Player targets SHALL never be frozen or have their tick skipped; they SHALL receive the slide immediately through the vanilla motion packet and a temporary `myvillage:combat_stun` movement slow for the hitstun.
- All reaction state SHALL clear on death, unload, dimension change, and server start or stop.

#### Scenario: A zombie is hit by move four
- **WHEN** move four damages a zombie
- **THEN** the zombie SHALL freeze for the hit-stop, then slide along the attack facing and stay staggered for the declared hitstun

#### Scenario: A player is hit
- **WHEN** a cultivation move damages another player
- **THEN** that player SHALL not be frozen, SHALL receive the slide at once, and SHALL be slowed for the hitstun

#### Scenario: A mob is juggled repeatedly
- **WHEN** the same mob is stunned again within 40 ticks
- **THEN** each further stun SHALL be shorter by the declared falloff

#### Scenario: A boss is hit
- **WHEN** the Ender Dragon, Wither, or Warden is damaged
- **THEN** it SHALL take damage and knockback rules but no freeze or stun

### Requirement: Hit impacts are broadcast as presentation-only data
After a successful hit the server SHALL send `CombatImpactPayload` to the attacker and every player tracking the attacker, containing only attacker entity id, action revision, move index, and up to 16 struck entity ids with one contact point each. It SHALL be registered clientbound only, SHALL carry no damage, health, knockback, or velocity, and SHALL be sent in addition to the unchanged attacker-only hit confirmation. The payload protocol version SHALL be `6`. Clients MAY use it to freeze and jitter struck entities visually for the move's hit-stop and to slow a remote attacker's animation; they SHALL NOT change any entity's server state or reply with a payload.

#### Scenario: A nearby player watches a hit
- **WHEN** a tracking client receives an impact payload
- **THEN** it MAY visually hold and jitter the struck entities and the attacker's animation for the hit-stop
- **AND** health, position, and damage SHALL still come only from ordinary server synchronization

#### Scenario: The payload codec is inspected
- **WHEN** the impact payload fields and registration direction are enumerated
- **THEN** only the declared ids, revision, move index, and contact points SHALL be present and it SHALL be clientbound only

### Requirement: Camera effects are attacker-local presentation
The attacker's client MAY add camera shake, per-move pitch/roll kicks, and FOV punches on its own hits and steps. They SHALL scale with the `screenEffectScale` (angles) and `fovEffectScale` (FOV) accessibility options, SHALL be reduced in third person, and SHALL never lock, aim, or rotate the player's view yaw or pitch. The client SHALL cancel the vanilla slowness FOV zoom caused only by the combat commitment and stun movement modifiers, leaving other speed-based FOV changes intact.

#### Scenario: Accessibility scale is zero
- **WHEN** Screen Effect Scale and FOV Effect Scale are set to `0`
- **THEN** no combat shake, kick, or FOV punch SHALL be visible

#### Scenario: The attacker swings while committed
- **WHEN** the server's commitment modifier slows the attacker
- **THEN** the attacker's FOV SHALL not zoom in because of that modifier

### Requirement: Combat debug visualization is operator-only transient and off by default
The server SHALL expose a permission-gated `/myvillage combat debug on|off` control that stores no persistent gameplay value. When enabled for an operator, bounded particles MAY show active samples and accepted contacts. Debug state SHALL default off, send no client-authored hitbox, and change no hit result.

#### Scenario: A normal player attacks
- **WHEN** debug was never enabled
- **THEN** no debug sample particles SHALL be emitted

#### Scenario: An operator enables debug
- **WHEN** active frames resolve after the command succeeds
- **THEN** particles SHALL correspond to server-computed samples without modifying selection or damage

### Requirement: Gate B proves one complete move before Gate C
Gate B SHALL prove Qingfeng registration/resources, configurable mode toggle, exact interception, empty attack intent, authoritative first-move selection/timing, PAL start/stop, active-frame hit volume, real damage, remote-player broadcast, durability rule, and action completion. Remaining four move implementations SHALL not be bulk-copied while this vertical slice has a failing required check.

#### Scenario: Gate B passes
- **WHEN** the first move completes every automated and required runtime check
- **THEN** implementation MAY extend the shared definitions/state/hit/animation surfaces to moves two through five

#### Scenario: The first move has a damage or synchronization defect
- **WHEN** its required hook, hit, recovery, or remote animation behavior fails
- **THEN** the defect SHALL be repaired in the shared path before Gate C work proceeds

### Requirement: Gate C completes all five moves and lifecycle behavior
Gate C SHALL add all remaining original animations, full combo/buffer/reset behavior, five distinct hit shapes, fifth-move server step, revisioned interruption/recovery, deterministic target caps, and multiplayer synchronization. Final completion SHALL not leave moves two through five as placeholder aliases of move one.

#### Scenario: Runtime definitions are enumerated after Gate C
- **WHEN** every move is loaded and exercised by tests
- **THEN** all five SHALL have distinct timing, active frames, multiplier, target cap, range, animation, and hitbox definition

#### Scenario: Lifecycle cleanup is exercised
- **WHEN** death, logout, dimension change, weapon change, mode change, mount, meditation, or advancement affects an active player
- **THEN** server session and remote animation cleanup SHALL follow the declared reason and no stale hit set/action SHALL survive

### Requirement: Scope remains the basic sword foundation
This change SHALL NOT add dodge, block, parry, stamina, poise, super armor, complex enemy hurt animation, launcher/air combo, sword projectile, spiritual-power cost, realm damage multiplier, technique proficiency, special sword art, flying-sword attack, lock-on camera, or global entity combat replacement. The declared server hitstun/slide reaction, chain windows, step impulses, and attacker-local camera effects are part of the basic foundation and are not poise, launchers, or lock-on.

#### Scenario: Shipped combat surfaces are enumerated
- **WHEN** Java registrations, payloads, key mappings, definitions, resources, and docs are inspected
- **THEN** they SHALL contain only the declared mode switch, Qingfeng Sword, five basic moves, target reaction, presentation feedback, debug visualization, and their supporting state/synchronization/validation surfaces
