# Alchemy Foundation

## Purpose

Provide a deterministic, survival-accessible alchemy loop with a physical furnace and bounded consumables.

## Requirements

### Requirement: Physical alchemy inventory
The mod SHALL expose a modeled placeable furnace, six herb items, three distinct reusable formula items and three distinct pill items, each with bilingual names, creative exposure and a survival acquisition recipe. The formula sheet SHALL identify its ingredients.

#### Scenario: Survival crafting
- **WHEN** a player follows documented plant infusion, formula and furnace recipes
- **THEN** every input needed for brewing is obtainable without administrator commands

### Requirement: Server-owned deterministic brewing
The furnace SHALL own five saved slots and brew from bundled validated rules after a manual one-batch start. A formula is reusable, herb order is interchangeable, and herbs/fuel SHALL be consumed exactly once with output creation. Active input slots SHALL be locked; invalidated inputs cancel progress, while full or incompatible output pauses without consuming ingredients. Chunk unload SHALL pause production; breaking SHALL drop held inventory exactly once. Owner, aptitude ceiling and rolled result SHALL persist with progress.

#### Scenario: Interrupted brew
- **WHEN** a valid partly brewed furnace is saved and reloaded
- **THEN** its inventory, fixed result and matching progress resume without duplicate costs or offline catch-up

#### Scenario: Invalid or obstructed brew
- **WHEN** ingredients mismatch or output is full
- **THEN** the furnace produces no output and consumes no input

### Requirement: Bounded medicines
Pills SHALL use server-owned bounded effects and a shared persistent use interval. Recovery SHALL clamp to capacity; healing SHALL stop on damage; stability support SHALL apply only to post-cap meditation within stage limits. Rejected use SHALL consume nothing, active effects SHALL not stack, and cultivation mutations SHALL use CultivationService.

#### Scenario: Recovery at capacity
- **WHEN** a player tries a recovery pill at full power
- **THEN** the pill is retained and no cooldown is charged

#### Scenario: Restart during medication interval
- **WHEN** a player reconnects or the server restarts during the interval
- **THEN** the remaining interval is preserved and no offline benefit accrues

#### Scenario: Stability support
- **WHEN** an active stability medicine is evaluated during progress gain, study or advancement
- **THEN** it grants no extra progress, study points or bypass of advancement conditions

See also: [alchemy-foundation technical note](../../../docs/ai-kb/49_alchemy.md), [alchemy-quality](../alchemy-quality/spec.md).
