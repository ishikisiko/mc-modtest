## REMOVED Requirements

### Requirement: Meditation controls use V B and G while H remains read-only
**Reason**: The H screen now provides bounded action buttons, and advancement
adds N to the same configurable control family.

**Migration**: Replace this requirement and the separate fourth-action
requirement with the unified V/B/G/N plus H Meditation-tab requirement below.

### Requirement: Change 5 adds one fourth action to the same bounded intent
**Reason**: Advancement is no longer an isolated later control addition; all
four actions now share one stable key-and-widget contract.

**Migration**: Replace it with the unified V/B/G/N plus H Meditation-tab
requirement below.

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Meditation controls use V B G N and the H meditation tab
The client SHALL retain configurable V for normal meditation, B for spirit
meditation, G for stop, N for advancement, and H for the cultivation screen.
The H screen SHALL expose a Meditation tab with normal, spirit, stop, and
advance buttons that send exactly the same actions as V, B, G, and N. Opening,
closing, switching, or rendering tabs SHALL send no action. Existing conflict
capture and repeat handling SHALL apply equally to keys and widgets.

#### Scenario: A meditation button is activated
- **WHEN** the local player activates normal, spirit, stop, or advance in H
- **THEN** the client SHALL send only the matching bounded action once

#### Scenario: The player switches tabs
- **WHEN** the player moves between Profile and Meditation without activating an action button
- **THEN** the client SHALL send no cultivation intent or profile mutation

#### Scenario: Keyboard control remains available
- **WHEN** no conflicting screen captures V, B, G, or N
- **THEN** each key SHALL retain the same action semantics as its H-screen button

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
