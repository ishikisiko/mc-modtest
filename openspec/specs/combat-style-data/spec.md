# combat-style-data Specification

## Purpose
Define how combat move sets and weapons are stored as bundled data, loaded on both sides, selected by held item, and kept server-authoritative, and how the client smoke probes follow the held weapon. See-also `docs/ai-kb/34_combat_data_and_capture.md` and `docs/ai-kb/32_pal_combat_integration.md`.

## Requirements
### Requirement: Combat styles and weapons are bundled data
The mod SHALL define each combat move set in a style file and each combat weapon in a weapon file under `data/<namespace>/combat/`, listed by `data/myvillage/combat/index.json`. The runtime SHALL load these files from the mod's own resources on both the client and the dedicated server and MUST NOT keep a second copy of any move's timing, hit shape, step, reaction, feedback, or camera values in Java source.

#### Scenario: Server and client load the same style
- **WHEN** a dedicated server and a client start with the same mod jar
- **THEN** both resolve `myvillage:basic_sword` to the same five moves with the same timing, hit shapes, steps, reactions, and feedback

#### Scenario: Invalid bundled data stops startup
- **WHEN** a listed style or weapon file is missing, has an unknown field, or violates a timing invariant
- **THEN** loading fails with an error that names the file and the field, and the mod does not continue with partial combat data

### Requirement: Style data satisfies the move invariants
A style SHALL have a unique id, positive combo timeout and minimum intent interval, ready-idle and mode-enter animation ids, and at least one move. Each move SHALL satisfy `0 <= active start <= active end < total_ticks`, `active start <= buffer_start_tick < total_ticks`, and `active end < chain_tick <= total_ticks`, with a step tick inside the move when a step is present. Move ids SHALL be unique across all styles.

#### Scenario: Chain tick inside the active window is rejected
- **WHEN** a move declares a `chain_tick` that is not after its active end
- **THEN** the style fails to load

#### Scenario: Duplicate move id across styles is rejected
- **WHEN** two styles declare a move with the same id
- **THEN** loading fails and names both styles

### Requirement: Held item selects the style
The server SHALL treat a player as armed for cultivation combat only when the main-hand item has a weapon entry, and SHALL run that weapon's style. Combat code MUST NOT name a specific weapon item. Changing to an item without a weapon entry, or to a weapon with a different style, during an action SHALL stop the action as a weapon change.

#### Scenario: Registered weapon attacks with its style
- **WHEN** a player in cultivation mode holding `myvillage:qingfeng_sword` sends an attack intent
- **THEN** the server starts move one of `myvillage:basic_sword`

#### Scenario: Unregistered item does not attack
- **WHEN** a player in cultivation mode holding an item with no weapon entry sends an attack intent
- **THEN** the server rejects the intent and starts no action

### Requirement: Authority boundary is unchanged
The two combat client-to-server payloads SHALL remain empty, and timing, hit, damage, and step decisions SHALL remain on the server. The impact broadcast SHALL identify the move by id and MUST NOT carry damage or health.

#### Scenario: Impact payload names the move
- **WHEN** the server lands a hit
- **THEN** the clientbound impact payload carries the attacker id, revision, move id, struck entity ids, and contact points only

### Requirement: Qingfeng behavior is preserved
After this change the Qingfeng sword SHALL keep its five moves with the same totals, active windows, buffer and chain ticks, damage multipliers, target caps, ranges, hit samples, steps, reactions, sounds, hit-stop, camera cues, first-person rig, and generated third-person animation.

#### Scenario: Loaded definition equals the previous constants
- **WHEN** the unit tests load `myvillage:basic_sword`
- **THEN** every move field, hit sample, feedback value, and camera value equals the value the removed Java constants held

#### Scenario: Generated animation is unchanged
- **WHEN** `python3 tools/gen_sword_pal_anims.py --check` runs after the generator reads timing from the style file
- **THEN** it reports the committed `sword_combat.json` as up to date

### Requirement: Client presentation follows the weapon entry
The client SHALL load the first-person rig and geometry named by each weapon entry on every resource reload and SHALL apply the first-person animator, arm, and trails to any registered weapon. A weapon whose rig or geometry is missing or invalid SHALL fall back to the vanilla hold without affecting other weapons.

#### Scenario: Invalid rig affects only its weapon
- **WHEN** one weapon's rig file fails validation on reload
- **THEN** that weapon uses the vanilla hold and the error names the rig, while other weapons keep their rigs

### Requirement: Smoke probes follow the held weapon and can freeze third person
The client smoke command SHALL resolve move indexes against the style of the main-hand weapon. `third_person <move> <tick>` SHALL hold the local player's full-body animation of that move at that tick until `third_person release`, `stop`, or a real action, and SHALL send no payload.

#### Scenario: Third-person freeze holds a pose
- **WHEN** a developer holding a registered weapon runs `/myvillage_pal_smoke third_person 2 5`
- **THEN** the local player model holds move two's pose at tick five, a `PAL_SMOKE` log line records the move and tick, and no combat intent is sent

#### Scenario: Probe without a weapon fails
- **WHEN** the main-hand item has no weapon entry
- **THEN** the probe returns failure and changes no pose

