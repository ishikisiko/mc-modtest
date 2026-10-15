## MODIFIED Requirements

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

## ADDED Requirements

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
