## ADDED Requirements

### Requirement: Meditation uses one server-authoritative transient state machine
The logical server SHALL own one in-memory session per player with states idle,
preparing-normal, preparing-spirit, meditating-normal, and meditating-spirit.
Preparation SHALL require 40 continuously eligible server ticks. Sessions SHALL
not be serialized in the cultivation profile, another attachment, block data,
or SavedData.

#### Scenario: Normal meditation starts
- **WHEN** an eligible idle player submits start-normal
- **THEN** the server SHALL transition from preparing-normal to meditating-normal only after 40 uninterrupted eligible ticks

#### Scenario: Spirit meditation starts
- **WHEN** an eligible idle player submits start-spirit
- **THEN** the server SHALL transition from preparing-spirit to meditating-spirit only after 40 uninterrupted eligible ticks

#### Scenario: The server restarts
- **WHEN** a player had any preparation or meditation state before server stop
- **THEN** the next server session SHALL begin idle and the persistent profile SHALL be unchanged by cleanup

### Requirement: The meditation intent payload is input-only and bounded
One serverbound payload SHALL contain exactly one of `START_NORMAL`,
`START_SPIRIT`, or `STOP`. It MUST NOT contain identity, coordinates, dimension,
rotation, velocity, duration, inventory, reserve, progress, requested result, or
breakthrough action. The server SHALL derive the sender from context, reject
unknown values, process on the server thread, and rate-limit duplicate starts.

#### Scenario: A legal start intent arrives
- **WHEN** a client sends one declared action
- **THEN** the server SHALL evaluate current authoritative state rather than accept a client-authored result

#### Scenario: Payload shape is enumerated
- **WHEN** codec fields and legal actions are inspected
- **THEN** only the three declared actions SHALL exist
- **AND** no `START_BREAKTHROUGH` action or N key binding SHALL exist in this change

#### Scenario: A duplicate stop arrives
- **WHEN** an idle player sends STOP repeatedly
- **THEN** handling SHALL remain idempotent without profile mutation or repeated effects

### Requirement: Meditation eligibility is revalidated on the server
A player SHALL be eligible only when alive, non-removed, survival/adventure,
not lifespan exhausted, awakened, learned current registered Basic Breathing,
on a supporting surface, not mounted, swimming, fall-flying, ability-flying,
sleeping, using an item, or in another conflicting cultivation state, and at
least 100 server ticks beyond the most recent successful positive damage.

#### Scenario: An initiated stable player starts
- **WHEN** every eligibility condition passes
- **THEN** the server SHALL accept the requested preparation mode

#### Scenario: Recent damage blocks start
- **WHEN** positive damage succeeded fewer than 100 server ticks ago
- **THEN** start SHALL be rejected with a recent-damage reason and the player SHALL remain idle

#### Scenario: Lifespan is exhausted
- **WHEN** consumed lifespan is at or above the current resolvable realm maximum
- **THEN** both normal and spirit starts SHALL be rejected server-side

#### Scenario: Spirit resources are absent in this state-only change
- **WHEN** an otherwise eligible player has no reserve and no low-grade spirit stone
- **THEN** this change MAY enter spirit state but SHALL not consume, credit, or grant anything

### Requirement: Movement and gameplay actions interrupt while camera rotation is allowed
Declared movement and gameplay actions SHALL interrupt meditation.
Any authoritative displacement exceeding `0.01` block on an axis, jump/leaving
support, positive damage, attack/swing, mining start, block/entity/item use,
mounting, swim/flight/sleep/use state, game-mode conflict, dimension change,
death, logout, or STOP SHALL end preparation or meditation. Yaw and pitch alone
SHALL not interrupt.

#### Scenario: A player looks around
- **WHEN** only yaw or pitch changes during a non-idle state
- **THEN** the current state SHALL continue

#### Scenario: A player moves or jumps
- **WHEN** server position exceeds tolerance or support fails
- **THEN** the session SHALL return to idle with one movement interruption result

#### Scenario: Damage lands during meditation
- **WHEN** successful positive damage occurs
- **THEN** the session SHALL stop immediately and a new 100-tick window SHALL begin

#### Scenario: An action produces multiple hooks
- **WHEN** attack, mining, or use emits more than one relevant event
- **THEN** the session SHALL stop exactly once

### Requirement: Client status feedback is transition-only and read-only
The server SHALL send compact clientbound state, preparation ticks when relevant,
and a stable reason only on accepted start, rejection, preparation completion,
interruption, or stop. It SHALL not send profile data every tick or allow the
client to install server state.

#### Scenario: Preparation completes
- **WHEN** the fortieth eligible preparation tick completes
- **THEN** the client SHALL receive one active-mode transition status

#### Scenario: Eligibility rejects start
- **WHEN** a server check fails
- **THEN** the client SHALL receive one translatable reason and remain idle

### Requirement: Meditation controls use V B and G while H remains read-only
The client SHALL register configurable V for normal, B for spirit, and G for
stop. H SHALL remain a read-only profile screen with no meditation controls.
This change SHALL not register N.

#### Scenario: A meditation key is pressed
- **WHEN** the local player presses V, B, or G without conflicting screen capture
- **THEN** the client SHALL send only the matching bounded action

#### Scenario: H is opened during an otherwise uninterrupted session
- **WHEN** the player opens the profile screen without another declared action
- **THEN** H SHALL remain presentation-only and SHALL not author meditation state

### Requirement: The state-machine slice grants no cultivation result
Preparation and meditation in this change SHALL not change progress, stability,
mastery, power, reserve, inventory, realm, or stage beyond the independent
lifespan clock. It SHALL not add pose, animation, HUD, breakthrough, or random outcome.

#### Scenario: A player remains active without the gain capability
- **WHEN** meditation continues for any duration
- **THEN** the profile and inventory SHALL remain unchanged by meditation state itself
