# cultivation-meditation Specification

## Purpose
TBD - created by archiving change add-cultivation-meditation. Update Purpose after archive.
## Requirements
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
`START_SPIRIT`, `STOP`, or `START_BREAKTHROUGH`. The same codec SHALL serve
configurable keys and H-screen buttons. It MUST NOT contain identity,
coordinates, dimension, rotation, velocity, elapsed ticks, affinity, inventory,
stone cost, gain rate, cap, reserve, progress, stability, target, duration, or
requested result. The server SHALL derive the sender and all authoritative
values, reject unknown actions, process on the server thread, and rate-limit
duplicate starts.

#### Scenario: A legal H-screen action arrives
- **WHEN** a button sends one declared action
- **THEN** the server SHALL evaluate current profile, definitions, session, inventory, and eligibility
- **AND** it SHALL not accept a displayed or client-authored cost, rate, target, or result

#### Scenario: Payload shape is enumerated
- **WHEN** codec fields and legal actions are inspected
- **THEN** only the four declared actions SHALL exist and no numeric gameplay field SHALL be encoded

#### Scenario: A duplicate stop arrives
- **WHEN** an idle player sends STOP repeatedly from a key or button
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

### Requirement: The state-machine slice grants no cultivation result
Preparation and meditation in this change SHALL not change progress, stability,
mastery, power, reserve, inventory, realm, or stage beyond the independent
lifespan clock. It SHALL not add pose, animation, HUD, breakthrough, or random outcome.

#### Scenario: A player remains active without the gain capability
- **WHEN** meditation continues for any duration
- **THEN** the profile and inventory SHALL remain unchanged by meditation state itself

### Requirement: Meditation and advancement share one exclusive session owner
The existing transient cultivation session manager SHALL add advancement states
without running a second simultaneous state machine for the same player.
Meditation starts SHALL be rejected during advancement, advancement starts
SHALL be rejected during preparation/meditation, and the common stop and
interruption routing SHALL remove the one current state idempotently.

#### Scenario: Both starts arrive in one server interval
- **WHEN** the server accepts one start before handling the other
- **THEN** only the first eligible session SHALL exist
- **AND** the second request SHALL be rejected as conflicting

### Requirement: Meditation controls use V B X N and the H meditation tab
The client SHALL retain configurable V for normal meditation, B for spirit
meditation, X for stop, N for advancement, and H for the cultivation screen.
The H screen SHALL expose a Meditation tab with normal, spirit, stop, and
advance buttons that send exactly the same actions as V, B, X, and N. Opening,
closing, switching, or rendering tabs SHALL send no action. MyVillage SHALL
leave `G` unreserved by default and SHALL NOT add GuideME-specific interception,
remapping, or automatic binding migration; ordinary configurable-key repeat and
screen handling SHALL remain unchanged.

#### Scenario: A meditation button is activated
- **WHEN** the local player activates normal, spirit, stop, or advance in H
- **THEN** the client SHALL send only the matching bounded action once

#### Scenario: The player switches tabs
- **WHEN** the player moves between Profile and Meditation without activating an action button
- **THEN** the client SHALL send no cultivation intent or profile mutation

#### Scenario: Keyboard control remains available
- **WHEN** no conflicting screen captures V, B, X, or N
- **THEN** each key SHALL retain the same action semantics as its H-screen button

#### Scenario: GuideME owns its default item-index key
- **WHEN** MyVillage and GuideME register their default client controls
- **THEN** MyVillage SHALL assign stop meditation to X instead of G
- **AND** MyVillage SHALL not intercept or rewrite GuideME's G binding

### Requirement: Ten-tick settlement cadence remains server-owned session state
An active meditation session SHALL count only server-observed eligible ticks and
SHALL make a progress settlement due after every tenth such tick. Preparation,
idle time, disconnected time, interrupted time after session removal, and
client-reported duration SHALL not contribute. A session end SHALL discard any
partial count below ten ticks.

#### Scenario: Ten eligible active ticks complete
- **WHEN** a server-owned normal or spirit session reaches its tenth continuously eligible active tick
- **THEN** exactly one settlement SHALL become due using current authoritative state

#### Scenario: Meditation stops after nine ticks
- **WHEN** interruption removes a session whose current settlement count is nine
- **THEN** those ticks SHALL not produce or persist a progress result

#### Scenario: A spirit session downgrades
- **WHEN** the due spirit batch lacks its complete server-derived stone cost
- **THEN** the existing session SHALL become normal without another 40-tick preparation
- **AND** the due tick SHALL be eligible for the normal settlement result
