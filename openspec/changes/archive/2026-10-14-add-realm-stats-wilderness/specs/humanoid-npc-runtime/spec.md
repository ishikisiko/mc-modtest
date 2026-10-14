## MODIFIED Requirements

### Requirement: The cultivator is summoned or projected, never spawned naturally
`myvillage:cultivator` SHALL be registered in mob category `misc` with a 0.6 by 1.9 block hitbox and an eye height of 1.67, with English and Chinese entity and spawn-egg names, a spawn egg `myvillage:cultivator_spawn_egg` in `myvillage:main`, and an empty loot table. It SHALL have no natural spawning: no spawn placement, biome modifier, or spawn biome tag. A cultivator SHALL appear only when summoned by a command or a spawn egg, or when WorldSimAvatars projects a ledger member at a realized sect gate or an eligible rogue in a configured wilderness region while a player is near; a sect avatar SHALL stand on the compound's open courtyard ground and show its name, realm, and sect. A wilderness avatar SHALL stand on safe loaded ground in the matching region and show its ledger name, realm, and rogue rank.

#### Scenario: Operator summons the cultivator
- **WHEN** an operator runs `/summon myvillage:cultivator ~ ~ ~`
- **THEN** a cultivator appears in any difficulty, stands, strolls, and looks at nearby players

#### Scenario: The simulation projects a sect's members
- **WHEN** a player stands in the courtyard of a compound built with `/myvillage world sect <id> build`
- **THEN** the sect's ledger members who are at the sect, up to the configured cap, stand on the courtyard ground as cultivators named name · realm · sect

#### Scenario: Wilderness rogue
- **WHEN** a local living rogue meets the configured regional realm range and a loaded safe placement is available
- **THEN** the ledger avatar manager may project that person within its bounded caps without adding a second identity or making the avatar persistent
