## MODIFIED Requirements

### Requirement: Cultivation snapshots flow only from server to owning client
The mod SHALL keep `myvillage:cultivation_snapshot` play-to-client and SHALL
carry an immutable current-v2 profile containing every v2 field. A separate
compact cultivation-time payload SHALL also be clientbound-only. Neither
payload SHALL allow the client to write realm, stage, root, progress, stability,
power, lifespan, reserve, techniques, or mastery.

#### Scenario: The server synchronizes one player
- **WHEN** `CultivationService#syncToClient` is called for a `ServerPlayer`
- **THEN** the current v2 profile SHALL be sent only to that owning player
- **AND** it SHALL not broadcast another player's private profile

#### Scenario: A client attempts to author v2 counters
- **WHEN** registered payload directions are inspected
- **THEN** no payload SHALL accept a client-provided lifespan or reserve value
- **AND** the server attachment SHALL remain authoritative

### Requirement: Snapshot synchronization occurs at authoritative lifecycle and mutation points
The server SHALL synchronize the current v2 profile on login, respawn, dimension
change, successful administrator mutation, successful ritual mutation, and each
successful 600-tick or forced lifespan commit. It SHALL send only final installed
profiles and SHALL NOT synchronize profile state every tick. A compact shared
time snapshot MAY be sent on login/respawn/dimension/configuration events and at
most once per 600 active server ticks.

#### Scenario: A lifespan batch commits
- **WHEN** pending lifespan is installed through `CultivationService`
- **THEN** the owning player SHALL receive at most one final v2 profile snapshot

#### Scenario: Time advances between batches
- **WHEN** fewer than 600 active ticks pass without another synchronization trigger
- **THEN** no periodic profile or time payload SHALL be sent for each tick

#### Scenario: A cultivation mutation fails
- **WHEN** a v2 profile mutation or batch validation fails
- **THEN** the prior profile SHALL remain installed and no changed profile snapshot SHALL be sent

### Requirement: A configurable key opens the read-only cultivation profile screen
The client SHALL retain `Open Cultivation Profile` on configurable key `H`.
While a local player is present and no other screen is open, consuming that key
SHALL open the non-pausing screen; H again or Escape SHALL close it. With current
profile/time snapshots, the screen SHALL show all prior fields plus schema 2,
calendar year/day, lifespan consumed, remaining/current maximum, reserve, and
exhausted/unavailable state. It SHALL remain sharp and send no payload.

#### Scenario: A player opens the v2 profile
- **WHEN** the owning client has current profile and time snapshots and activates H
- **THEN** every v2 and time status field SHALL be displayed from immutable caches
- **AND** no cultivation payload SHALL be sent by opening or rendering the screen

#### Scenario: The diagnostic screen renders its backdrop
- **WHEN** the screen draws its owned translucent backdrop and profile content
- **THEN** it SHALL not invoke `Screen#render` after drawing that content
- **AND** the vanilla background post-process SHALL not blur already-rendered content

#### Scenario: One snapshot is missing
- **WHEN** either current profile or time status is unavailable
- **THEN** the affected section SHALL show an explicit waiting/unavailable state rather than fabricated values

#### Scenario: A profile realm is unavailable
- **WHEN** maximum lifespan cannot resolve from current registries
- **THEN** raw realm and consumed/reserve values SHALL remain visible
- **AND** maximum, remaining, and exhaustion SHALL use an unavailable marker

## ADDED Requirements

### Requirement: The time snapshot is compact clientbound and side safe
The time payload SHALL carry only non-negative shared calendar ticks, positive
ticks-per-day and days-per-year values, and server-derived presentation status
needed by the owning client. It SHALL use the existing registrar architecture,
`StreamCodec`, and `enqueueWork`; common/server registration SHALL not load
client-only classes.

#### Scenario: A dedicated server registers time synchronization
- **WHEN** payload registration runs on a dedicated server
- **THEN** the time payload SHALL register clientbound without duplicate handlers or client classloading

#### Scenario: Time data is decoded
- **WHEN** a valid time snapshot round-trips through its real buffer codec
- **THEN** every counter and scale value SHALL be preserved exactly
