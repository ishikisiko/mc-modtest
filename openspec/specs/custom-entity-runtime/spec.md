# Custom Entity Runtime Specification

## Purpose

Define the contract, runtime, resource, texture, spawn, validation, and human-acceptance requirements for the first complete MyVillage custom entity slice.

## Requirements

### Requirement: Entity Contract precedes implementation
The system SHALL define and schema-validate an Entity Contract for `myvillage:simple_fox` before Java runtime or pack resource implementation is considered complete. The contract SHALL record stable identity, physical dimensions, attributes, behavior inheritance, synchronized and persisted state ownership, rendering route, resources, natural spawning, acceptance checks, assumptions, and non-goals.

#### Scenario: Contract validates before implementation gates
- **WHEN** the simple fox implementation is validated
- **THEN** its contract passes the custom-entity schema before compile, resource, or build gates are reported green

### Requirement: Simple fox has an independent runtime identity
The mod SHALL register `myvillage:simple_fox` as a creature with explicit dimensions, tracking settings, fox-compatible attributes, and a saveable and summonable `EntityType`. The implementation SHALL inherit vanilla fox movement and AI without adding custom combat or per-tick world scans.

#### Scenario: Entity can be summoned
- **WHEN** an operator runs `/summon myvillage:simple_fox ~ ~ ~`
- **THEN** a living simple fox entity is created with its registered attributes and inherited fox behavior

#### Scenario: Entity state survives reload
- **WHEN** a simple fox is saved and its world is reloaded
- **THEN** the inherited fox persistent state remains valid without requiring fabricated custom NBT fields

### Requirement: Client rendering remains side-safe
The simple fox renderer SHALL use the vanilla fox model and animation path with a MyVillage texture. Renderer, model, model-layer, and other client-only references MUST NOT be loaded from common or dedicated-server code.

#### Scenario: Dedicated server loads the entity
- **WHEN** the mod starts in the dedicated-server run
- **THEN** entity registration and data loading complete without resolving client renderer classes

#### Scenario: Client resolves the custom texture
- **WHEN** a client renders `myvillage:simple_fox`
- **THEN** the dedicated renderer uses `assets/myvillage/textures/entity/simple_fox/simple_fox.png`

### Requirement: Entity resources are complete and intentional
The mod SHALL provide English and Chinese entity and spawn-egg names, a spawn-egg item model, an intentional loot table, and a spawn egg exposed through `myvillage:main`. The entity SHALL reuse legal vanilla fox sounds unless real custom OGG resources and matching sound events are supplied.

#### Scenario: Player obtains the spawn egg
- **WHEN** a player runs `/give @s myvillage:simple_fox_spawn_egg` or opens the MyVillage creative tab
- **THEN** the simple fox spawn egg is available with a translated name and creates the registered entity

#### Scenario: Loot data resolves
- **WHEN** data packs load the simple fox loot table
- **THEN** `data/myvillage/loot_table/entities/simple_fox.json` parses as an intentional first-iteration empty table

### Requirement: Natural spawning has both required layers
Natural simple fox spawning SHALL be defined by a MyVillage biome tag and NeoForge biome modifier and SHALL also register a Java spawn placement predicate. Initial spawning SHALL be conservative, limited to taiga-family biomes, and use small groups.

#### Scenario: Natural-spawn resources are paired
- **WHEN** the custom-entity validator inspects simple fox spawning
- **THEN** it finds the biome tag, biome modifier, matching entity id, and Java spawn placement registration together

#### Scenario: Spawn placement rejects invalid ground
- **WHEN** the engine evaluates simple fox natural spawning at a location that fails the fox ground predicate
- **THEN** the spawn placement predicate rejects that location

### Requirement: Texture production is deterministic and reviewable
The visible texture SHALL have a recorded UV truth source, filled island mask, semantic region metadata, deterministic transformation provenance, fixed rectangular output dimensions, and alpha/color validation. Concept and atlas image generation SHALL use Codex built-in imagegen without direct API credentials, and every atlas candidate SHALL be constrained by a local deterministic composite mask with the adopted or rejected verdict recorded.

#### Scenario: Texture validation passes
- **WHEN** the final simple fox texture is checked
- **THEN** its dimensions, alpha footprint, palette bound, and unused texels satisfy the recorded vanilla fox UV evidence

### Requirement: Automated success remains separate from visual acceptance
The implementation SHALL run focused entity validation, unit tests, a practical jar build, and a dedicated-server startup smoke. In-game summon, spawn egg, save/reload, natural-frequency, multiplayer, and multi-view appearance checks SHALL remain explicitly pending until observed, and the visual status SHALL remain `human_review_pending` until the owner records a verdict.

#### Scenario: Build passes without human verdict
- **WHEN** all automated checks and dedicated-server startup pass but no in-game visual verdict exists
- **THEN** the change reports automated validation as green and visual acceptance as `human_review_pending`

#### Scenario: Owner accepts after in-game review
- **WHEN** the owner confirms the summon, spawn egg, persistence, natural-frequency, multiplayer, and multi-view checks pass
- **THEN** the visual verdict is recorded as `accepted` and the change is eligible for closeout
