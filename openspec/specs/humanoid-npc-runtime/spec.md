# humanoid-npc-runtime Specification

## Purpose
Define the humanoid NPC framework (a body without a disposition, generated layered model, cut-out texture and clips at twice the vanilla texel density, validation) and its first NPC `myvillage:cultivator` (修仙者). See-also `docs/ai-kb/39_humanoid_npcs.md`, `genops/contracts/entities/cultivator.yaml`, `tools/npcgen/README.md`, and `hostile-beast-runtime` for the model and animation file schemas this framework shares.

## Requirements
### Requirement: An NPC is a body without a disposition
`NpcEntity` SHALL extend `PathfinderMob` and give an NPC only a body in the world: it floats, strolls, looks at nearby players, and looks around. It MUST NOT register a target goal, an attack, a trade, or a dialogue; friend-or-foe behaviour SHALL be added by a later design, not assumed by this framework. An NPC SHALL NOT despawn by distance, SHALL NOT accept a leash, and SHALL be removed by nothing but death, a command, or the world simulation withdrawing an avatar it projected. An avatar is an NPC carrying a world-ledger person id (synced to clients; -1 for a summoned NPC): it SHALL NOT be saved with its chunk, SHALL NOT stroll (it still looks at players and around), SHALL NOT be attackable or take damage except from a source that bypasses invulnerability, SHALL NOT burn or be pushed, and SHALL do nothing when a player interacts with it. Only the world simulation SHALL make an NPC an avatar; a summoned NPC SHALL behave as described without it.

#### Scenario: A cultivator ignores an attacker
- **WHEN** a player hits a summoned `myvillage:cultivator`
- **THEN** it takes the damage and neither targets nor attacks the player

#### Scenario: A summoned cultivator stays when the player leaves
- **WHEN** every player moves beyond the despawn distance and returns
- **THEN** the summoned cultivator is still there

#### Scenario: An avatar follows the ledger, not the chunk
- **WHEN** every player moves away from a realized sect compound and later returns
- **THEN** its avatars are withdrawn while no player is near, none is saved with its chunk, and on the return there is again exactly one avatar per shown ledger member

#### Scenario: An avatar cannot be hurt
- **WHEN** a player hits an avatar
- **THEN** the avatar takes no damage and does not react

### Requirement: NPC client files are generated and layered
For an NPC `ns:name` the client SHALL read `assets/ns/npc/name_model.json`, `assets/ns/npc/name_animations.json`, and `textures/entity/name/name.png`. These three files SHALL be written only by `tools/npcgen` from `tools/npcgen/defs/<name>.py` and MUST NOT be hand-edited. The model and animation files SHALL use the beast schema-1 formats. The look SHALL come from real geometry layers (a cube per garment layer, band, or ornament that stands proud of what is under it) and from a texture painted per texel from its position on the model: form light, shadows cast by the layer above, crevices beside raised bands, folds, dye, and trim. A large cloth face MUST NOT be one flat fill.

#### Scenario: Generated files are current
- **WHEN** `python3 -m tools.npcgen build cultivator --check` runs
- **THEN** it exits 0 only when the three files on disk equal the generator's output

#### Scenario: Layers stack outward on the chest
- **WHEN** the cultivator's rest pose is measured
- **THEN** the collar bands lie in front of the robe, the vest in front of them, the vest's border bands in front of the vest, and the belt's buckle in front of all of them

### Requirement: The model file may carry a scale
A schema-1 model file MAY contain `scale`, a positive number read as 1 when absent. The beast and NPC renderers SHALL scale the whole model by it before vanilla seats the model on the ground, and gait rates SHALL account for it. A file with scale 0.5 is authored in units of 1/32 block, so its geometry and texels are twice as fine as a vanilla mob's. A file without `scale` SHALL keep its bytes and its drawn size.

#### Scenario: A half-scale model draws at twice the density
- **WHEN** the cultivator's model file declares scale 0.5 and a 64-unit-tall figure
- **THEN** the figure is drawn 2 blocks tall with 32 texels per block

#### Scenario: Zero scale is rejected
- **WHEN** a model file declares scale 0
- **THEN** loading fails naming the file and the `scale` field

### Requirement: Shell layers are cut out, never blended
An NPC texture SHALL be drawn with the entity cut-out render type. A texel of a shell cube (hair, an open vest) with zero alpha SHALL be a hole showing the layer under it. Every texel's alpha MUST be 0 or 255, and a cube that is not a declared shell MUST have every texel of its UV islands painted.

#### Scenario: The vest opens down the front
- **WHEN** the cultivator's vest shell is painted
- **THEN** its front face is transparent between its border bands and its sides and back are whole

### Requirement: Idle always runs and walk follows the ground
An NPC's animations SHALL contain looping `idle` and `walk` clips whose channels name bones of its model. The client SHALL run `idle` from the first client tick and drive `walk` through vanilla limb swing at the rate that keeps the planted foot where it is, measured from the clip and the model's scale. In the cultivator's walk the planted foot SHALL stay on the ground and travel back at an even speed, the swinging foot SHALL lift, and the vest panels and sash SHALL turn with the leg under them so the skirt does not come through.

#### Scenario: A clip without walk is rejected
- **WHEN** the animations file has no `walk` clip or one that does not loop
- **THEN** the renderer fails to load the NPC naming the clip, and the validator reports it

### Requirement: NPC rendering stays client-side
NPC renderers and layer definitions SHALL be registered only from the client-dist event subscriber. Common code under `entity/**` MUST NOT import `net.minecraft.client` or `com.example.myvillage.client` types.

#### Scenario: Dedicated server loads the NPC
- **WHEN** the mod starts on a dedicated server
- **THEN** the NPC's entity type and attributes load without resolving any client class

### Requirement: The cultivator is summoned or projected, never spawned naturally
`myvillage:cultivator` SHALL be registered in mob category `misc` with a 0.6 by 1.9 block hitbox and an eye height of 1.67, with English and Chinese entity and spawn-egg names, a spawn egg `myvillage:cultivator_spawn_egg` in `myvillage:main`, and an empty loot table. It SHALL have no natural spawning: no spawn placement, biome modifier, or spawn biome tag. A cultivator SHALL appear only when summoned by a command or a spawn egg, or when the world simulation projects a ledger member who is at a realized sect gate as an avatar while a player is near; an avatar SHALL stand on the compound's open courtyard ground and show its name, realm, and sect.

#### Scenario: Operator summons the cultivator
- **WHEN** an operator runs `/summon myvillage:cultivator ~ ~ ~`
- **THEN** a cultivator appears in any difficulty, stands, strolls, and looks at nearby players

#### Scenario: The simulation projects a sect's members
- **WHEN** a player stands in the courtyard of a compound built with `/myvillage world sect <id> build`
- **THEN** the sect's ledger members who are at the sect, up to the configured cap, stand on the courtyard ground as cultivators named name · realm · sect

### Requirement: NPC validation and evidence
`tools/validate_custom_entities.py` SHALL validate every NPC that `tools/npcgen` builds: the model and animation schemas, the required clips, a texture whose size equals the model's atlas and whose alpha is binary, the generator definition, names in both languages, the spawn egg and its colours, the loot table, the Java registration against the Entity Contract, renderer registration in the client class, and the absence of natural spawning. `python3 -m tools.combat_capture npc` SHALL collect in-game stills and walk footage as developer evidence. The look, the motion, and every real-client surface SHALL remain `not_verified` until the owner observes them.

#### Scenario: A contract that drifts from the registration fails
- **WHEN** the contract's `physical.height` differs from the registered hitbox
- **THEN** the validator reports the difference and exits non-zero
