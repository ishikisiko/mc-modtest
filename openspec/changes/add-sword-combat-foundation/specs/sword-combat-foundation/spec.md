## ADDED Requirements

### Requirement: Qingfeng Sword is a complete independent diamond-tier sword
The mod SHALL register `myvillage:qingfeng_sword` as a `SwordItem` using mapped `Tiers.DIAMOND` and `SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)`. It SHALL therefore have durability `1561`, held attack damage `7.0`, held attack speed `1.6`, diamond enchantment value and repair ingredient behavior, normal durability/Mending compatibility, and membership in the vanilla sword item tag. It SHALL appear in `myvillage:main` with English `Qingfeng Sword`, Chinese `青锋剑`, an ordinary item model, an original pixel texture, and a shaped diamond-sword-equivalent recipe.

#### Scenario: The sword is obtained
- **WHEN** a player opens `myvillage:main` or runs `/give @s myvillage:qingfeng_sword`
- **THEN** the registered localized sword SHALL have a resolved model and texture

#### Scenario: The sword attributes are inspected
- **WHEN** the Qingfeng Sword is held in the main hand
- **THEN** its tier, durability, attack-damage modifier, attack-speed modifier, enchantment value, and diamond repair ingredient SHALL match the mapped vanilla diamond sword

#### Scenario: The sword is enchanted or repaired
- **WHEN** normal sword-compatible enchantment, anvil repair, Mending, or durability loss logic evaluates the stack
- **THEN** the Qingfeng Sword SHALL participate through ordinary sword/tier/item hooks

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
`CombatModeTogglePayload` and `SwordAttackIntentPayload` SHALL contain no mode value, combo index, move id, target entity, hit result, damage, hitbox, position, yaw, velocity, movement endpoint, animation completion, or client tick authority. The server SHALL derive the sender and all authoritative values. The payload protocol version SHALL advance once without changing the existing flying-sword bitset or cultivation snapshot semantics.

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
Only the declared late-recovery portion of an active move SHALL accept one next-input buffer. A second buffered input SHALL be rejected and SHALL not replace, stack, or skip a move. Too-early and over-rate input SHALL be rejected without extending the action or advancing the combo. State progression SHALL use server game ticks and remain correct when real-time TPS drops.

#### Scenario: One late input is buffered
- **WHEN** an intent arrives during the current move's buffer window and no input is buffered
- **THEN** exactly one next move SHALL start at the declared server transition

#### Scenario: The player clicks repeatedly in the same buffer window
- **WHEN** two or more intents arrive before the buffered move starts
- **THEN** only the first SHALL be retained and the combo SHALL advance by one

#### Scenario: Input arrives before the buffer window
- **WHEN** an intent arrives during startup or active frames too early for buffering
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

### Requirement: Hit detection uses broad and narrow phases
At each active server tick, `CombatHitResolver` SHALL derive a broad union AABB and then run move-specific OBB, capsule, or swept-volume narrow-phase tests from server position, server body yaw, current action tick, and local definition samples. It SHALL NOT represent all five moves as one fixed inflated AABB. Visual tolerance SHALL be bounded to at most `0.25` horizontally and `0.15` vertically.

#### Scenario: A broad-phase candidate misses the narrow shape
- **WHEN** an entity lies inside the union AABB but outside every move sample plus tolerance
- **THEN** it SHALL not be hit

#### Scenario: The player turns after the action starts
- **WHEN** an active tick resolves samples
- **THEN** the resolver SHALL use the action's declared server-authoritative facing policy and SHALL not accept a client-provided yaw or hitbox

### Requirement: Each move has a distinct geometric vocabulary
Move one SHALL use a narrow long center thrust and avoid easy side/rear hits. Move two SHALL sweep right-to-left horizontally through approximately 100-120 degrees. Move three SHALL sample a left-low to right-high rising diagonal and apply no prolonged launch. Move four SHALL sample a thicker right-high to left-low diagonal that is narrower than move two and uses its higher multiplier. Move five SHALL combine a long thrust with the complete actual server step sweep.

#### Scenario: Side targets surround move one
- **WHEN** one target is on the center line and another is equally near at the side or rear
- **THEN** the center target MAY be hit while the side/rear target SHALL fail the narrow thrust

#### Scenario: Move two crosses several legal targets
- **WHEN** candidates lie along its sampled horizontal arc
- **THEN** at most three SHALL be selected in deterministic order

#### Scenario: Move three succeeds
- **WHEN** a target intersects its rising diagonal samples
- **THEN** damage and only the declared light vanilla-style knockback SHALL apply
- **AND** no long-duration floating state SHALL be created

#### Scenario: Move four and move two are compared
- **WHEN** their narrow-phase envelopes are measured
- **THEN** move four SHALL be thicker along its diagonal but have a narrower horizontal sweep than move two

### Requirement: Fifth-move stepping is bounded, collision-safe, and server-owned
Move five SHALL request no more than `0.8` block forward. The server SHALL clip the path, require collision-free player space and safe support below the destination, apply normal collision-aware movement, and use actual start-to-end displacement for the full swept hit volume. It SHALL not move from a client coordinate/velocity/endpoint and SHALL not pass through a solid wall or step knowingly over an unsupported cliff edge.

#### Scenario: Open supported ground is ahead
- **WHEN** move five reaches its declared step tick
- **THEN** the server MAY move the player forward by at most `0.8` block and SHALL test the actual swept volume

#### Scenario: A solid wall is ahead
- **WHEN** the intended step or player box intersects the wall
- **THEN** movement SHALL stop before collision and the hit sweep SHALL not extend through the wall

#### Scenario: A cliff edge is ahead
- **WHEN** the destination lacks declared supporting collision within the safety depth
- **THEN** the forward step SHALL be suppressed or shortened to supported ground

#### Scenario: A target lies between start and end
- **WHEN** the actual server step crosses its bounding box during active frames
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
For each selected target the server SHALL fire the NeoForge player-attack gate, use `playerAttack` damage source, derive base damage from current `Attributes.ATTACK_DAMAGE`, apply the move multiplier and current item target bonus, invoke `EnchantmentHelper.modifyDamage`, and call ordinary `Entity#hurt`. A successful hit SHALL use current knockback/enchantment helpers and post-attack enchantment effects. It SHALL not call `ServerPlayer#attack` for the same move, create a second vanilla hit, or trigger vanilla sweeping, critical, or sprint-attack behavior.

#### Scenario: A normal target is hit
- **WHEN** no attack or incoming-damage event cancels the action
- **THEN** armor, protection, invulnerability frames, NeoForge incoming/pre/post damage events, and the declared move damage SHALL apply through the standard hurt sequence

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
The server SHALL broadcast accepted action starts to the attacker and tracking players with attacker entity id, move id, server start tick, and monotonically increasing revision. It SHALL broadcast authoritative stops with attacker id, revision, and a bounded reason when an active action is canceled or corrected. Client code SHALL animate remote players only from those broadcasts.

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
This change SHALL NOT add dodge, block, parry, stamina, poise, super armor, complex enemy hurt animation, launcher/air combo, sword projectile, spiritual-power cost, realm damage multiplier, technique proficiency, special sword art, flying-sword attack, lock-on camera, or global entity combat replacement.

#### Scenario: Shipped combat surfaces are enumerated
- **WHEN** Java registrations, payloads, key mappings, definitions, resources, and docs are inspected
- **THEN** they SHALL contain only the declared mode switch, Qingfeng Sword, five basic moves, debug visualization, and their supporting state/synchronization/validation surfaces
