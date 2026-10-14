# innate-fates Specification

## Purpose
TBD - created by archiving change add-innate-fates-foundation. Update Purpose after archive.
## Requirements
### Requirement: Persistent server-owned fate choice
The system SHALL store immutable fate state with copy-on-death and replace it only through CultivationService. Existing roots SHALL remain unchanged. Unconfirmed awakened players SHALL have one eventual permanent confirmation, including old saves.

#### Scenario: Login and death retain choice
- **WHEN** a confirmed player reconnects, changes dimension or respawns
- **THEN** selected IDs, roll sequence and cooldowns remain, modifiers do not stack, and ordinary refresh grants no healing

### Requirement: Bounded synchronized selection
The server SHALL offer six unique eligible candidates, allow one lock, require three selections within the configurable point budget, and enforce high-tier and exclusive-group limits. Client messages SHALL contain only bounded intent, slot or selection mask, and seen revision. The server SHALL reject stale, malformed, throttled and already-confirmed mutations.

#### Scenario: Low budget with expensive lock
- **WHEN** a player locks an expensive candidate and the server budget is reduced
- **THEN** reroll preserves that candidate but supplies a legal low-cost triple, and confirmation rechecks the current server budget

#### Scenario: Forged confirmation
- **WHEN** a client submits an old revision or a mask outside the six current slots
- **THEN** the server rejects the request without changing state or granting any effect

### Requirement: Data-owned first-batch effects
The first pool SHALL contain only implemented definitions. Balance numbers SHALL live in data. Effects SHALL remain server-authoritative, with each damage contribution applied once, stable attribute replacement and no modification of bypass damage.

#### Scenario: Empty or unconfirmed choice
- **WHEN** a player has not confirmed any fates
- **THEN** all existing combat and cultivation values retain their previous behavior

### Requirement: Recovery and kill lifecycle
Alchemist recovery SHALL be non-lethal rescue only, bounded over time and protected by a persisted cooldown. Hunter recovery SHALL require a personally attributed hostile kill and SHALL modify spiritual power only through CultivationService. Death SHALL end remaining healing without refreshing the cooldown.

#### Scenario: Restart during cooldown
- **WHEN** a player reconnects or the server restarts after a recovery trigger
- **THEN** the remaining cooldown is not reset and no offline healing is paid in a burst

### Requirement: Page-based interaction and legacy entry
The H panel SHALL expose fate selection through a PanelPage reading PanelContext and synchronized definitions. Normal players SHALL be able to choose after root awakening, while reset SHALL require operator authority. English commands SHALL have structurally equivalent pinyin aliases under both cultivation roots.

#### Scenario: Existing awakened character
- **WHEN** an old character opens the fate page for the first time
- **THEN** their cultivation and root remain intact and they may roll and confirm once
