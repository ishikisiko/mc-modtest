## REMOVED Requirements

### Requirement: The H profile screen remains a read-only ritual result view
**Reason**: H now includes a Meditation tab with explicit action-intent buttons,
while profile values and results remain read-only and server authoritative.

**Migration**: Preserve every ritual-phase presentation and sharp rendering
in the Profile tab; route buttons only through the bounded action payload.

### Requirement: The read-only H screen presents current-stage gain status
**Reason**: Gain status moves into a dedicated Meditation tab and legacy reserve
must no longer be displayed.

**Migration**: Present progress/cap, affinity, rates, cost, status, and
advancement context in the Meditation tab without presenting reserve.

## MODIFIED Requirements

### Requirement: Cultivation snapshots flow only from server to owning client
The mod SHALL keep `myvillage:cultivation_snapshot` play-to-client and SHALL
carry an immutable current-v3 profile containing every v3 field, including
spiritual affinity and inert legacy reserve. Separate time and runtime-status
payloads SHALL remain clientbound-only. The bounded action-intent payload SHALL be
play-to-server but MUST NOT carry or write realm, stage, root, affinity,
progress, stability, power, lifespan, reserve, techniques, mastery, cost, rate,
target, or result.

#### Scenario: The server synchronizes one player
- **WHEN** `CultivationService#syncToClient` is called for a `ServerPlayer`
- **THEN** the current v3 profile SHALL be sent only to that owning player
- **AND** it SHALL not broadcast another player's private profile

#### Scenario: A client submits a cultivation action
- **WHEN** registered payload directions and fields are inspected
- **THEN** the only cultivation serverbound data SHALL be one bounded enumerated action
- **AND** the server attachment and resolved gameplay numbers SHALL remain authoritative

### Requirement: A configurable key opens the read-only cultivation profile screen
The client SHALL retain `Open Cultivation Profile` on configurable key `H` and
the existing non-pausing, H/Escape toggle behavior. The screen SHALL contain
Profile and Meditation tabs in one stable responsive layout. The Profile tab
SHALL present prior profile, root, technique, calendar, lifespan, and
advancement information plus schema `3`, spiritual affinity, and dynamic
stability cap, while omitting legacy reserve. The Meditation tab SHALL present
current progress/cap, affinity-based normal result, fixed spirit result,
current source-stage stone cost, stability's locked/active affinity rate and
dynamic cap, the no-stone-cost state after full progress, mastery context,
latest runtime status, and normal, spirit, stop, and advance action buttons.
Presentation SHALL remain sharp.

#### Scenario: A player opens the v3 Profile tab
- **WHEN** current profile and time snapshots are available
- **THEN** the screen SHALL show spiritual affinity and every still-supported profile/time value
- **AND** it SHALL not show, spend, or describe `meditationQiReserve`

#### Scenario: A player opens the Meditation tab
- **WHEN** the current stage and snapshots resolve
- **THEN** the screen SHALL show the server-contract normal and spirit outputs, resolved cost, progress/stability caps, stability lock or rate, gate context, and latest session state
- **AND** no action SHALL be sent until one command button is activated

#### Scenario: Full progress changes the displayed cost phase
- **WHEN** the current stage progress is already full and stability is below its cap
- **THEN** the Meditation tab SHALL show affinity-paced stability gain and no stone cost
- **AND** it SHALL not continue advertising the progress-phase per-batch stone charge as current

#### Scenario: One snapshot or registry definition is missing
- **WHEN** the data required for a section cannot resolve
- **THEN** that section SHALL show a waiting or unavailable marker rather than fabricate affinity, cost, cap, target, or status

#### Scenario: The screen renders at a supported GUI scale
- **WHEN** either tab draws its backdrop, labels, values, tabs, and widgets
- **THEN** it SHALL preserve the established sharp-content ordering
- **AND** text and controls SHALL remain within their stable panel bounds without incoherent overlap

### Requirement: The snapshot StreamCodec preserves complete valid profiles
`CultivationSnapshotPayload.STREAM_CODEC` SHALL encode and decode every v3
profile field, including spiritual affinity and inert reserve, and SHALL
preserve syntactically valid unknown definition ids. Invalid profile invariants
SHALL fail decoding rather than produce an unvalidated cache value. Presentation
code SHALL not expose legacy reserve merely because the codec preserves it.

#### Scenario: A non-default v3 snapshot round-trips
- **WHEN** a snapshot containing non-default affinity, reserve, root, techniques, progress, stability, power, lifespan, and unknown ids is encoded and decoded
- **THEN** the decoded snapshot SHALL equal the original and the buffer SHALL be consumed correctly

#### Scenario: Invalid affinity is decoded
- **WHEN** snapshot data contains negative spiritual affinity
- **THEN** payload decoding SHALL fail and the client cache SHALL retain its previous value

## ADDED Requirements

### Requirement: Meditation-tab buttons are advisory action controls only
Each Meditation-tab button SHALL send exactly one existing bounded action and
SHALL use translatable labels plus a stable visible enabled/disabled state.
Client-side enabled state and displayed cost/rate SHALL be advisory only; the
server MUST revalidate and SHALL reject every invalid action. The client SHALL not predict
or install inventory, progress, mode, or advancement outcomes.

#### Scenario: A stale client enables spirit meditation
- **WHEN** the UI displays sufficient resources but the authoritative ordinary inventory no longer funds a batch
- **THEN** the client SHALL send only `START_SPIRIT`
- **AND** the server SHALL decide downgrade, cost, and result from current state

#### Scenario: A button receives one click
- **WHEN** a visible action button is activated once
- **THEN** it SHALL emit at most one matching intent and await server status

#### Scenario: The client disconnects with the Meditation tab open
- **WHEN** logout clears client cultivation caches
- **THEN** a later connection SHALL not display the prior profile or runtime session as current
