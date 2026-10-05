# hostile-beast-runtime Specification

## Purpose
Define the hostile beast framework (data-driven attack moves, stagger immunity, generated client model and animation files, debug commands, validation) and its first beast `myvillage:demon_wolf`. See-also `docs/ai-kb/38_hostile_beasts.md`, `genops/contracts/entities/demon_wolf.yaml`, and `custom-entity-runtime` for the vanilla-route fox.

## Requirements
### Requirement: Beast moves and tuning are bundled data
Each beast SHALL have one schema-1 data file at `data/<namespace>/beast/<name>.json`, listed by entity id in `data/myvillage/beast/index.json`, holding its attributes, chase settings, stagger clip name, and attack moves. The runtime SHALL load these files from the mod's own resources on both sides and MUST NOT keep a second copy of any move timing, range, cooldown, weight, hit box, lunge, knockback, immunity window, or attribute value in Java source. Loading SHALL reject an unknown or missing field, a move whose ticks violate `0 <= turn_lock_tick <= windup_ticks <= first active tick <= last active tick < total_ticks`, an `immune_ticks` window outside the move, a lunge tick outside `[turn_lock_tick, last active tick]`, a duplicate move id across beasts, and a move or stagger clip name that is reserved (`idle`, `walk`, `run`) or reused.

#### Scenario: Invalid beast data stops startup
- **WHEN** a listed beast file has an active tick at or after `total_ticks`
- **THEN** loading fails with an error naming the file and the field, and the mod does not continue with partial beast data

#### Scenario: A retune needs no Java change
- **WHEN** a move's cooldown or use range is changed in the data file
- **THEN** the rebuilt mod uses the new value without any Java edit

### Requirement: Moves run on the server with a locked aim
A beast SHALL start a move only when it is on the ground, not stunned, has line of sight to its target, the target's horizontal distance lies in the move's `use_range`, the move's cooldown is over, and `move_gap_ticks` have passed since the previous move ended; ready moves SHALL be chosen by `weight`. A running move SHALL advance one tick per server AI step, turn toward the target only before `turn_lock_tick`, lock its yaw and aim point at `turn_lock_tick`, apply one lunge impulse along the locked yaw at `lunge.tick`, test its hit box in the locked frame on the active ticks with each victim hit at most once and at most `maximum_targets` victims, and start its cooldown when it ends. Damage, victims, and movement SHALL be decided on the server only.

#### Scenario: Stepping away after the lock makes the pounce miss
- **WHEN** the target moves out of the locked hit box after `turn_lock_tick`
- **THEN** the beast keeps its locked yaw and aim point and the move deals no damage to that target

#### Scenario: A hit-stop pauses the move
- **WHEN** a combat hit freezes the beast for its hit-stop
- **THEN** the move tick does not advance while the beast's tick is skipped

### Requirement: Stagger resistance goes through a generic hook
The combat package SHALL expose `combat/runtime/StaggerResistant` and SHALL NOT name any beast class, id, or package. When a combat hit lands on a target that resists stagger at that moment, the hit SHALL still deal its damage and apply the hit-stop freeze, and SHALL apply no hitstun and no knockback impulse. A beast SHALL resist exactly while a move runs and its last executed tick lies inside that move's `immune_ticks`, and for the same window SHALL carry a transient knockback-resistance modifier so vanilla knockback does not push it out of the move. Outside the window a combat stun SHALL cancel the running move, start the move's cooldown at `chase.cancelled_cooldown_ticks`, and show the stagger clip.

#### Scenario: Hit inside the immune window
- **WHEN** a player's combat move hits the beast on a tick inside the running move's `immune_ticks`
- **THEN** the beast loses health and freezes for the hit-stop, is not stunned, is not pushed, and continues the move

#### Scenario: Hit outside the immune window
- **WHEN** the same hit lands during recovery
- **THEN** the beast is stunned as any mob, its move is cancelled, and clients show the stagger clip

### Requirement: Client files are generated and follow the server ticks
For a beast `ns:name` the client SHALL read `assets/ns/beast/name_model.json` (bones and cubes mapping one-to-one onto vanilla `ModelPart`), `assets/ns/beast/name_animations.json` (clips mapping one-to-one onto vanilla `AnimationDefinition` keyframes), `textures/entity/name/name.png`, and the emissive `textures/entity/name/name_eyes.png`. These four files SHALL be written only by `tools/beastgen` from `tools/beastgen/defs/<name>.py` and MUST NOT be hand-edited. The animations SHALL contain looping `idle`, `walk`, and `run` clips and a non-looping clip for the stagger and for every move, and each move clip's length SHALL equal `total_ticks / 20` seconds. Clips SHALL be presentation only and timed from synced server state; the client SHALL send nothing for a beast.

#### Scenario: Generated files are current
- **WHEN** `python3 -m tools.beastgen build demon_wolf --check` runs
- **THEN** it exits 0 only when the four files on disk equal the generator's output

#### Scenario: A move clip of the wrong length is rejected
- **WHEN** a move's `total_ticks` changes without regenerating the animations
- **THEN** the renderer fails to load the beast naming the clip, and the validator reports the length mismatch

### Requirement: A running move is drawn on the server's path
While a move is shown, the client SHALL apply each beast position update in one step instead of vanilla's three-step smoothing, and SHALL play the move clip `MOVE_CLIP_DELAY_TICKS` behind the newest synced move tick so the clip shows the tick whose position is drawn, re-anchoring it only when it drifts `CLIP_RESYNC_TICKS` from that. Under `/tick freeze` the client SHALL finish any pending position step and show the server's move tick exactly. Outside moves vanilla smoothing SHALL apply. The one-step update and the clip delay SHALL change together.

#### Scenario: Pounce landing is drawn on the ground
- **WHEN** a pounce lands and the server reports the beast on the ground
- **THEN** the client draws the body at the server's landing position with the clip's landing pose, not still in the air behind it

#### Scenario: Frozen still shows the exact tick
- **WHEN** the world is frozen and stepped to move tick k
- **THEN** the beast is drawn at the server's position of tick k with the clip pose of tick k

### Requirement: Beast rendering stays client-side
Beast renderers and layer definitions SHALL be registered only from the client-dist event subscriber. Common code under `entity/**` MUST NOT import `net.minecraft.client` or `com.example.myvillage.client` types.

#### Scenario: Dedicated server loads the beast
- **WHEN** the mod starts on a dedicated server
- **THEN** the beast's entity type, attributes, and data load without resolving any client class

### Requirement: The demon wolf is summoned only
`myvillage:demon_wolf` SHALL be registered as a `monster` with a 1.3 by 1.45 block hitbox and an eye height of 1.2, with English and Chinese entity and spawn-egg names, a spawn egg `myvillage:demon_wolf_spawn_egg` in `myvillage:main`, and an empty loot table. It SHALL have no natural spawning: no spawn placement, biome modifier, or spawn biome tag. Summoned and egg-spawned wolves SHALL NOT despawn by distance.

#### Scenario: Operator summons the wolf
- **WHEN** an operator runs `/summon myvillage:demon_wolf ~ ~ ~` in a difficulty above Peaceful
- **THEN** a demon wolf appears with the attributes from its data file and targets the nearest player it can see

#### Scenario: No natural spawn resource exists
- **WHEN** the custom-entity validator inspects the wolf
- **THEN** it finds no spawn placement, biome modifier, or biome tag for it

### Requirement: Beast debug commands are server probes
The mod SHALL provide `/myvillage beast move <targets> <move_id>`, `/myvillage beast status <targets>`, and `/myvillage beast debug on|off` at permission level 2. `move` SHALL start the named move on the selected beasts on their next AI step regardless of range, cooldown, and gap; `status` SHALL print each selected beast's move state; `debug` SHALL toggle `BEAST_DEBUG` server-log lines for move starts, ends, staggers, and hits. These commands SHALL NOT be presented as gameplay or owner acceptance.

#### Scenario: Forcing a move for a still
- **WHEN** an operator runs `/myvillage beast move @e[type=myvillage:demon_wolf] myvillage:demon_wolf_pounce` under `/tick freeze` and steps ticks
- **THEN** the wolf runs the pounce from tick 0 and its client clip shows the server's move tick on each step

### Requirement: Beast validation and acceptance stay separate
`tools/validate_custom_entities.py` SHALL validate every beast in the data index: data invariants, model and clip files and their cross-file rules, texture and glow sizes, generator ownership, translations, spawn egg, loot table, Java registration against the Entity Contract, client-only renderer registration, the beast-neutral combat package, and the absence of natural spawning. The Entity Contract SHALL name tuned fields and refer to the data file instead of repeating tuned values. The release gate SHALL run the validator and each beast's generator `--check`. Headless capture output SHALL be developer evidence, and every look, feel, sound, reload, and multiplayer surface SHALL stay `not_verified` in the README ledger until the owner observes it on a physical client.

#### Scenario: Automation passes without an owner session
- **WHEN** the validator, tests, generator check, build, and headless capture pass but the owner has not played against the wolf
- **THEN** the README ledger keeps the look, fight feel, sound, `F3+T` reload, and multiplayer rows `not_verified`
