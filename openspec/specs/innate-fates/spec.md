# innate-fates Specification

## Purpose
Define server-owned innate-fate choice, testing-stele awakening interaction and persistent data-driven effects. See also: [Innate Fates](../../../docs/ai-kb/48_innate_fates.md).
## Requirements
### Requirement: Persistent server-owned fate choice
The system SHALL store immutable fate state with copy-on-death and replace it only through CultivationService. Existing roots SHALL remain unchanged. Unconfirmed awakened players SHALL have one eventual permanent confirmation, including old saves.

#### Scenario: Login and death retain choice
- **WHEN** a confirmed player reconnects, changes dimension or respawns
- **THEN** selected IDs, roll sequence and cooldowns remain, modifiers do not stack, and ordinary refresh grants no healing

### Requirement: Bounded synchronized selection
The server SHALL offer six unique eligible candidates, allow one lock, require three selections within the configurable point budget, and enforce exclusive-group limits. There SHALL be no additional limit on how many selected fates are high tier. Client messages SHALL contain only bounded intent, slot or selection mask, and seen revision. The server SHALL reject stale, malformed, throttled and already-confirmed mutations.

#### Scenario: Low budget with expensive lock
- **WHEN** a player locks an expensive candidate and the server budget is reduced
- **THEN** reroll preserves that candidate but supplies a legal low-cost triple, and confirmation rechecks the current server budget

#### Scenario: Forged confirmation
- **WHEN** a client submits an old revision or a mask outside the six current slots
- **THEN** the server rejects the request without changing state or granting any effect

#### Scenario: Multiple high-tier fates fit the budget
- **WHEN** a current three-fate selection contains multiple high-tier definitions in distinct groups and its total cost fits the server budget
- **THEN** both client eligibility and server confirmation SHALL accept it without a rarity-count rejection
- **AND** insufficient budget, duplicate groups, stale state and invalid sessions SHALL still be rejected

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
The testing stele SHALL open an independent awakening screen that first presents the synchronized spiritual root and then allows three-of-six fate selection. The H panel SHALL retain a read-only Fates PanelPage reading PanelContext and synchronized definitions, showing confirmed results or directing an unconfirmed player to the testing stele. H SHALL expose no roll, lock, selection or confirmation control. English commands SHALL have structurally equivalent pinyin aliases under both cultivation roots. Reset SHALL require operator authority.

#### Scenario: Existing awakened character
- **WHEN** an old awakened character uses the testing stele for the first time
- **THEN** their cultivation and root SHALL remain intact and they SHALL be able to choose and confirm once in the awakening screen

#### Scenario: H opens before confirmation
- **WHEN** an unconfirmed player opens H Fates
- **THEN** the page SHALL direct them to the testing stele without sending a fate mutation

#### Scenario: H opens after confirmation
- **WHEN** a confirmed player opens H Fates
- **THEN** the page SHALL show the three stored results and synchronized effects without selection controls

### Requirement: Fate mutations require a live testing-stele session
Every ordinary fate roll, lock and confirmation request, whether a packet or self-targeted command (including an operator's self-command), SHALL require a server-owned session established by a real testing-stele interaction. Each mutation SHALL revalidate the player is alive, in the session dimension, within the permitted interaction distance and beside the unchanged testing-stele block. Session state SHALL bind a strictly positive server-issued token to the player and stele; each network mutation SHALL carry that token, and a stale close token SHALL NOT invalidate a newer session. Session state SHALL be transient and invalidated on screen closure, logout, death, dimension change, teleport and administrative reset. Spectators SHALL not mutate fates through a session. The targeted `roll <player>` and `confirm <player> <mask>` diagnostic bypasses SHALL require explicit permission-level-2 authorization of the executing command source and SHALL remain documented fixture paths; they SHALL preserve ordinary fate eligibility and immutable confirmation rules.

#### Scenario: Mutation arrives without a session
- **WHEN** an ordinary client or command requests a fate change without a valid session
- **THEN** the server SHALL reject it without changing revision, candidates, root, selected fates or effects

#### Scenario: Session anchor is lost
- **WHEN** the player moves out of range, changes dimension, dies, logs out, closes the screen or loses the testing-stele block
- **THEN** a later ordinary mutation SHALL fail until the player interacts with a valid testing stele again

#### Scenario: Privileged target syntax is attempted by a non-operator
- **WHEN** a non-operator attempts a diagnostic bypass or targeted mutation
- **THEN** the command source SHALL fail permission checks rather than inheriting the target's authority

### Requirement: Reopening preserves awakening progress
Opening the awakening screen SHALL generate an initial fate pool only for an unconfirmed awakened player whose pool is empty. Existing candidates, revision, lock, root and confirmed choices SHALL remain unchanged by reopening. The screen SHALL preserve reachable fixed controls for six candidates, three selections, budget, lock, explicit reroll and two-step confirmation at GUI 480×270, 427×240 and 320×240 in English and Chinese.

#### Scenario: An unfinished draft is reopened
- **WHEN** an unconfirmed player closes and reuses a testing stele
- **THEN** the same root, candidates, revision and lock SHALL resume without an implicit reroll

#### Scenario: A confirmed character reuses a testing stele
- **WHEN** the stored fate choice is already confirmed
- **THEN** the awakening screen SHALL present the existing result without generating candidates or granting another confirmation

#### Scenario: Smallest supported layout is rendered
- **WHEN** the awakening screen is rendered at 320×240 in either supported language
- **THEN** all six candidates and final action controls SHALL be reachable without overlap or clipping that prevents choice
