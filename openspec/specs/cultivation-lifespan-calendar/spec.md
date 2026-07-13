# cultivation-lifespan-calendar Specification

## Purpose
TBD - created by archiving change add-cultivation-lifespan-calendar. Update Purpose after archive.
## Requirements
### Requirement: The shared cultivation calendar uses Overworld SavedData
The system SHALL persist non-negative `elapsedCalendarTicks` in one Overworld
`SavedData` record. A server-post-tick coordinator SHALL increment it exactly
once per effective server tick when at least one online player is in survival
or adventure mode, regardless of player dimension. It SHALL pause when no such
player is online and while the server is stopped.

#### Scenario: Eligible players are online across dimensions
- **WHEN** one or more survival/adventure players are online in any dimensions for one server tick
- **THEN** `elapsedCalendarTicks` SHALL increase by exactly one rather than once per player or dimension

#### Scenario: Only excluded players are online
- **WHEN** all online players are creative or spectator
- **THEN** the shared calendar SHALL not advance

#### Scenario: Vanilla time is changed
- **WHEN** sleep, `/time set`, daylight-cycle changes, weather, or dimension time changes occur
- **THEN** `elapsedCalendarTicks` SHALL remain governed only by effective server ticks and eligible-player presence

### Requirement: Personal lifespan advances only for the eligible living player
Each player's `lifespanConsumedTicks` SHALL increase once per server tick only
while that player is online, alive, non-removed, and in survival or adventure
mode. Offline, dead, creative, and spectator time SHALL not consume that
player's lifespan. Dimension and ordinary activity SHALL not change the rate.

#### Scenario: Two eligible players have different online sessions
- **WHEN** one player remains online while another disconnects
- **THEN** the online player's lifespan SHALL continue and the offline player's lifespan SHALL pause while the shared calendar continues

#### Scenario: A player is dead before respawn
- **WHEN** an eligible-mode player is not alive during the death/respawn interval
- **THEN** that player's lifespan SHALL not advance during that interval

#### Scenario: Activity type changes
- **WHEN** an eligible living player mines, walks, idles, or later meditates for equal tick counts
- **THEN** each activity SHALL consume lifespan at the same one-tick-per-tick rate

### Requirement: Cultivation time scale is configurable and reinterprets raw ticks
Server configuration SHALL default to `24000` effective ticks per cultivation
day and `6` cultivation days per year. Values SHALL be positive and all derived
products SHALL use checked arithmetic. Raw calendar and lifespan counters SHALL
not be rescaled when either setting changes; the new scale SHALL intentionally
reinterpret all existing raw ticks and SHALL emit an explicit operator warning.

#### Scenario: Default scale is used
- **WHEN** configuration retains its defaults
- **THEN** one cultivation year SHALL equal `144000` effective ticks
- **AND** an 80-year mortal maximum SHALL equal `11520000` consumed ticks

#### Scenario: An operator changes the scale
- **WHEN** either configured value changes for an existing world
- **THEN** prior raw calendar and lifespan ticks SHALL be converted with the new values
- **AND** the server SHALL warn that displayed dates, remaining life, and exhaustion can change retroactively

#### Scenario: Configuration overflows a derived product
- **WHEN** configured scale and a realm lifespan cannot be multiplied safely
- **THEN** the configuration or status calculation SHALL fail with a controlled diagnostic rather than wrap

### Requirement: Realm definitions determine maximum lifespan
The maximum lifespan for a player SHALL be resolved dynamically from the
current registered realm and SHALL not be stored in the profile or a Java
constant. The shipped values SHALL be mortal `80`, qi refining `120`, and
foundation establishment `240` cultivation years.

#### Scenario: A player enters qi refining
- **WHEN** the authoritative realm changes from mortal to qi refining
- **THEN** maximum lifespan SHALL immediately resolve as 120 years without resetting consumed ticks

#### Scenario: A stored realm is unavailable
- **WHEN** the profile names a realm absent from current registry access
- **THEN** the raw profile and consumed ticks SHALL remain unchanged
- **AND** maximum, remaining, and exhaustion presentation SHALL be unavailable rather than guessed

### Requirement: Warnings and exhaustion are derived from remaining lifespan
The service SHALL derive warnings at 10, 5, and 1 remaining cultivation years
and exhaustion when consumed ticks are greater than or equal to the current
realm maximum. Warnings SHALL be de-duplicated per online session; login already
inside a threshold SHALL emit only the most urgent applicable warning once.
Consumed ticks SHALL remain monotonic after exhaustion.

#### Scenario: A mortal crosses the warning thresholds
- **WHEN** a mortal reaches consumed ages 70, 75, and 79 under the default scale
- **THEN** the server SHALL emit the 10-, 5-, and 1-year remaining warnings respectively

#### Scenario: A player reaches the maximum
- **WHEN** consumed ticks reach the current realm maximum
- **THEN** the derived state SHALL become exhausted and remaining lifespan SHALL display as zero
- **AND** no automatic death, reset, or reincarnation SHALL occur

#### Scenario: A later realm raises the maximum above consumed age
- **WHEN** an authoritative realm change makes the current maximum exceed consumed ticks
- **THEN** the derived exhausted state SHALL become false without reducing consumed ticks

### Requirement: Lifespan persistence is batched through CultivationService
The runtime SHALL hold pending lifespan ticks in memory and submit one checked
immutable profile replacement through `CultivationService` every 600 server
ticks. Logout, death/replacement, dimension change, and clean server stopping
SHALL force pending state through the same boundary without adding a cultivation
`PlayerEvent.Clone` copy. Failed commits SHALL retain pending ticks for retry.

#### Scenario: A batch interval completes
- **WHEN** an eligible player accumulates 600 pending server ticks
- **THEN** one profile replacement SHALL add the pending amount and send at most one resulting profile snapshot

#### Scenario: A player disconnects before the interval
- **WHEN** an eligible player logs out with pending lifespan ticks
- **THEN** those ticks SHALL be submitted before the session state is discarded

#### Scenario: A hard crash occurs between ordinary saves
- **WHEN** the process stops without a clean lifecycle or vanilla disk save
- **THEN** validation and documentation SHALL not claim the 600-tick batch is a disk-durability guarantee

### Requirement: Calendar and lifespan arithmetic never wraps negative
All additions and scale products SHALL use checked or saturating arithmetic.
Elapsed and consumed counters SHALL saturate at `Long.MAX_VALUE` if no smaller
valid result exists and SHALL never wrap to a negative value.

#### Scenario: A counter reaches its numeric ceiling
- **WHEN** another effective tick would overflow a persisted counter
- **THEN** that counter SHALL remain `Long.MAX_VALUE` and derived exhaustion SHALL remain safe

### Requirement: The H screen displays read-only cultivation time status
The non-pausing H screen SHALL display 1-based cultivation year/day, consumed
lifespan, remaining/current maximum lifespan, meditation reserve, and exhausted
or unavailable state from immutable client caches. At elapsed tick zero it SHALL
show year 1 day 1. It SHALL not send a mutation or expose controls.

#### Scenario: The default mortal profile is viewed
- **WHEN** current snapshots are present at raw calendar tick zero
- **THEN** H SHALL show year 1 day 1, consumed 0, remaining `80 / 80`, reserve 0, and not exhausted

#### Scenario: Time status has not arrived
- **WHEN** the profile exists but no time snapshot is cached
- **THEN** H SHALL show an explicit waiting/unavailable time state rather than fabricate calendar values

### Requirement: This change does not enforce final lifespan consequences
Exhaustion SHALL be exposed as a server-authoritative eligibility result for
later meditation and advancement services. This change SHALL NOT kill the
player, clear cultivation data, remove items, add attribute penalties, start
reincarnation, or implement meditation or breakthrough behavior.

#### Scenario: An exhausted player remains in the world
- **WHEN** consumed ticks exceed the current maximum
- **THEN** the profile SHALL remain intact and the player SHALL not be automatically killed by this capability
