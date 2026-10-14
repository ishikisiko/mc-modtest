## RENAMED Requirements

- FROM: `### Requirement: The demon wolf is summoned only`
- TO: `### Requirement: The demon wolf supports regional natural spawning`

## MODIFIED Requirements

### Requirement: The demon wolf supports regional natural spawning
`myvillage:demon_wolf` SHALL be registered as a `monster` with a 1.3 by 1.45 block hitbox and an eye height of 1.2, with English and Chinese entity and spawn-egg names, a spawn egg `myvillage:demon_wolf_spawn_egg` in `myvillage:main`, and an empty loot table. Natural spawning SHALL use a biome modifier, spawn placement, overworld region eligibility and persistent realm assignment under regional-wilderness. Summoned and egg-spawned wolves SHALL NOT despawn by distance.

#### Scenario: Operator summons the wolf
- **WHEN** an operator runs `/summon myvillage:demon_wolf ~ ~ ~` in a difficulty above Peaceful
- **THEN** a demon wolf appears with the attributes from its data file and targets the nearest player it can see

#### Scenario: Natural spawn resources agree
- **WHEN** the custom-entity validator inspects the wolf
- **THEN** it validates the declared natural spawning resources and placement rather than forbidding them

### Requirement: Beast validation and acceptance stay separate
`tools/validate_custom_entities.py` SHALL validate every beast in the data index: data invariants, model and clip files and their cross-file rules, texture and glow sizes, generator ownership, translations, spawn egg, loot table, Java registration against the Entity Contract, client-only renderer registration, the beast-neutral combat package, and the declared natural spawning contract. The Entity Contract SHALL name tuned fields and refer to the data file instead of repeating tuned values. The release gate SHALL run the validator and each beast's generator `--check`. Headless capture output SHALL be developer evidence, and every look, feel, sound, reload, and multiplayer surface SHALL stay `not_verified` in the README ledger until the owner observes it on a physical client.

#### Scenario: Automation passes without an owner session
- **WHEN** the validator, tests, generator check, build, and headless capture pass but the owner has not played against the wolf
- **THEN** the README ledger keeps the look, fight feel, sound, `F3+T` reload, and multiplayer rows `not_verified`
