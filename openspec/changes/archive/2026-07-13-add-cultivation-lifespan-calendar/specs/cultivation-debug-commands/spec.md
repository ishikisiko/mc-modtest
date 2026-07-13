## MODIFIED Requirements

### Requirement: The info command reports the complete current profile
The mod SHALL provide `/myvillage cultivation info [target]` and its existing
structurally equivalent aliases. Output SHALL include schema version, realm,
stage, cultivation progress, stability, current spiritual power,
`lifespanConsumedTicks`, `meditationQiReserve`, spiritual root or `unawakened`,
and every learned technique id with mastery.

#### Scenario: A new player is inspected
- **WHEN** an operator runs `info` for a player with the default profile
- **THEN** output SHALL show schema `2`, mortal/unawakened ids, all numeric fields including lifespan/reserve as zero, and no learned techniques

#### Scenario: Stored ids are unavailable
- **WHEN** the inspected v2 profile contains an id absent from current registries
- **THEN** output SHALL retain and mark the raw id as `unavailable`
- **AND** inspection SHALL not mutate or reset the profile

### Requirement: Reset and scalar commands mutate through CultivationService
The existing scalar/reset routes SHALL mutate through `CultivationService`.
The reset, setprogress, setstability, and setpower English/pinyin routes SHALL
retain their argument and permission contracts and SHALL mutate through
`CultivationService`. Reset SHALL install the exact current-v2 default, including
zero lifespan and reserve. This change SHALL not add commands that let players
author calendar, lifespan, or reserve values.

#### Scenario: A profile is reset
- **WHEN** an operator runs reset for a target
- **THEN** the service SHALL install the exact default v2 profile and synchronize immediately

#### Scenario: A scalar amount is invalid
- **WHEN** an operator supplies negative progress or power, or stability outside `0..100`
- **THEN** command/service validation SHALL reject the input and leave every v2 field unchanged

#### Scenario: A player requests clock mutation
- **WHEN** the command trees are enumerated after this change
- **THEN** no unprivileged or new administrator calendar/lifespan/reserve setter SHALL exist
