## ADDED Requirements

### Requirement: Cultivation progress is current-stage progress
`cultivationProgress` SHALL represent progress within the profile's current
realm stage, not lifetime cultivation earned. Ordinary gameplay settlement
SHALL apply no progress above the current registered stage cap and SHALL carry
no excess into a later stage. Migration and privileged administrator data MAY
retain a raw value above the cap; settlement SHALL treat such a value as capped
without normalizing it.

#### Scenario: Normal settlement reaches the current cap
- **WHEN** a player's applicable progress gain is greater than the remaining current-stage capacity
- **THEN** the replacement profile SHALL contain exactly the cap
- **AND** no progress SHALL be banked for a later advancement

#### Scenario: Existing raw progress is above the cap
- **WHEN** a migrated or administrator-created profile has progress above its current stage cap
- **THEN** settlement SHALL preserve the raw value and add no further progress

### Requirement: Meditation reserve is a separate durable balance
`meditationQiReserve` SHALL remain a non-negative persistent meditation-only
balance. It SHALL NOT be read as, converted to, or substituted for
`currentSpiritualPower`; meditation gain SHALL NOT spend or restore current
spiritual power. Every successful profile replacement and interruption SHALL
preserve unspent reserve exactly.

#### Scenario: Spirit meditation spends reserve
- **WHEN** an applicable spirit bonus point is settled
- **THEN** reserve SHALL decrease by exactly one for that bonus point
- **AND** current spiritual power SHALL remain unchanged

#### Scenario: Meditation stops with reserve remaining
- **WHEN** a session is interrupted or stopped after a prior stone conversion
- **THEN** the unspent reserve SHALL remain in the persistent profile

### Requirement: A gain settlement installs one immutable profile replacement
A successful cultivation settlement SHALL compute progress, stability, Basic
Breathing mastery, and reserve as one proposed immutable profile and submit it
through `CultivationService` once. Runtime managers, payload handlers, and
inventory helpers MUST NOT mutate the attachment or a retrieved profile/map
directly. A rejected settlement SHALL install and synchronize nothing.

#### Scenario: Several fields become due together
- **WHEN** one settlement produces progress, stability, mastery, and reserve spend
- **THEN** one validated replacement SHALL contain all final values
- **AND** no intermediate profile SHALL be installed

#### Scenario: Settlement validation fails
- **WHEN** the proposed gain profile violates a profile or registry invariant
- **THEN** the previous immutable profile SHALL remain installed
- **AND** no changed profile snapshot SHALL be sent
