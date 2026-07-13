# cultivation-state-synchronization Specification

## Purpose

Define owning-client snapshot delivery, read-only client caching, payload
direction, mutation authority, and dedicated-server side safety.
## Requirements
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

### Requirement: Definition registries are not duplicated in profile snapshots
`CultivationSnapshotPayload` SHALL NOT contain complete realm, spiritual-element, or technique definition records. Client definition lookup SHALL use the network-synchronized datapack registries, while the snapshot SHALL preserve the profile's raw `ResourceLocation` references.

#### Scenario: A snapshot contains learned techniques
- **WHEN** the server sends a profile whose learned-technique map contains several ids
- **THEN** the snapshot SHALL contain those ids and mastery values
- **AND** it SHALL NOT contain the referenced `TechniqueDefinition` objects

#### Scenario: A snapshot references an unavailable definition
- **WHEN** the client cannot resolve one snapshot id in its synchronized registry access
- **THEN** the client SHALL retain the raw id in the snapshot
- **AND** presentation code SHALL treat the definition as unavailable rather than rejecting the entire snapshot

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

### Requirement: Client cultivation state is a read-only latest-snapshot cache
`ClientCultivationState` SHALL expose read-only access to the latest immutable snapshot and SHALL restrict replace/clear operations to the internal client synchronization path. It SHALL clear on client disconnect and SHALL NOT write to a server player attachment or calculate authoritative replacements.

#### Scenario: The client receives a snapshot
- **WHEN** the clientbound payload handler accepts a valid snapshot
- **THEN** the cache SHALL replace its previous value atomically with the new immutable value

#### Scenario: The client disconnects from a server
- **WHEN** the client logout event fires
- **THEN** the cache SHALL become empty before another connection can observe the stale profile

#### Scenario: Client UI code reads the cache
- **WHEN** client presentation code accesses `ClientCultivationState`
- **THEN** it SHALL receive a read-only profile view
- **AND** it SHALL have no API to submit or install server-authoritative values

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

### Requirement: Payload handling uses the current NeoForge main-thread contract
The clientbound handler SHALL schedule cache replacement with `IPayloadContext#enqueueWork`. Payload registration SHALL use the current `PayloadRegistrar#playToClient` API and a `StreamCodec` over `RegistryFriendlyByteBuf`; it SHALL NOT use `SimpleChannel` or an obsolete Forge networking API.

#### Scenario: A snapshot is handled from a networking context
- **WHEN** NeoForge invokes the snapshot handler
- **THEN** cache replacement SHALL run through `enqueueWork`
- **AND** the handler SHALL NOT mutate client state directly from an unverified networking thread

### Requirement: Common and server code remains physical-side safe
Common attachment, registry, service, event, command, payload-registration, and payload classes MUST NOT import or directly resolve `net.minecraft.client` classes or other physical-client-only implementations. Client cache installation and disconnect handling SHALL live behind a `Dist.CLIENT` subscriber or an equivalent side-isolated entry point. Any common snapshot receiver bridge SHALL contain only common-safe types and SHALL not require client classloading on a dedicated server.

#### Scenario: A dedicated server loads the mod
- **WHEN** the dedicated server registers cultivation attachments, registries, events, commands, and payloads
- **THEN** it SHALL complete without loading `ClientCultivationState`, `Minecraft`, `LocalPlayer`, or another client-only implementation

#### Scenario: A physical client initializes
- **WHEN** the client-only subscriber is loaded on `Dist.CLIENT`
- **THEN** it SHALL install the snapshot cache receiver and client disconnect cleanup

### Requirement: Cultivation registration preserves the flying-sword protocol
`ModPayloads` SHALL retain one `RegisterPayloadHandlersEvent` registration path and SHALL add the cultivation clientbound payload through the existing registrar architecture. `FlyingSwordInputPayload` SHALL remain play-to-server with its existing flags, wire format, handler registration, and server ownership/passenger/world/liveness checks. Cultivation state SHALL NOT be mixed into the flying-sword payload.

#### Scenario: Both payloads register
- **WHEN** payload handlers are registered during mod startup
- **THEN** the existing flying-sword input payload SHALL register in the serverbound direction
- **AND** the cultivation snapshot SHALL register in the clientbound direction
- **AND** neither payload type SHALL have duplicate handler registration

#### Scenario: Flying-sword input is handled after cultivation integration
- **WHEN** an existing valid or invalid flying-sword input payload reaches the server
- **THEN** its flags and server validation behavior SHALL match the pre-change contract

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

### Requirement: Initiation adds no client-authoritative mutation path
The initiation change SHALL reuse the existing `CultivationSnapshotPayload` profile shape and clientbound registration. It MUST NOT add an awaken, root-generation, affinity-selection, inheritance, or technique-learning play-to-server payload. Client code SHALL NOT receive the Overworld seed or expose an API that selects or installs a root or technique.

#### Scenario: Payload registration is inspected after initiation integration
- **WHEN** the registered payload types and directions are enumerated
- **THEN** `myvillage:cultivation_snapshot` SHALL remain clientbound-only
- **AND** no cultivation play-to-server mutation payload SHALL exist
- **AND** the flying-sword input payload SHALL remain the existing independent serverbound payload

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

### Requirement: Meditation adds one bounded intent and one clientbound status path
Payload registration SHALL add the three-action meditation intent play-to-server
and runtime status play-to-client through the existing single registrar. The
intent SHALL control only transient session requests and SHALL not carry or
directly write profile values. Existing clientbound cultivation snapshots/time
status and serverbound flying-sword input SHALL retain their ids, directions,
codecs, and authorization behavior.

#### Scenario: All payloads register on a dedicated server
- **WHEN** the common payload registrar initializes
- **THEN** meditation intent SHALL register serverbound and status clientbound without duplicate handlers or client-only classloading
- **AND** flying-sword input SHALL remain unchanged

#### Scenario: A meditation intent is handled
- **WHEN** the server receives a valid action
- **THEN** the current server-thread contract SHALL invoke server-owned session logic
- **AND** the handler SHALL not call `ServerPlayer#setData` directly

### Requirement: Client meditation state is a read-only latest-status cache
The physical client SHALL cache only the latest immutable runtime status for
presentation and SHALL clear it on disconnect. It SHALL have no API to install a
server session, profile, reserve, progress, or result.

#### Scenario: The client disconnects
- **WHEN** client logout fires
- **THEN** meditation status SHALL clear before another connection can observe it

### Requirement: Cultivation settlements synchronize only final changed profiles
Each successful 100-tick settlement that installs a changed profile SHALL send
the owning client exactly one final profile snapshot through the existing
clientbound path. Transient tick counters and fixed-point remainders SHALL not
be serialized or synchronized. A batch with no whole profile change, a rejected
batch, and ordinary meditation ticks between batches SHALL send no profile
snapshot.

#### Scenario: One batch changes several profile fields
- **WHEN** progress, stability, mastery, and reserve change in one settlement
- **THEN** the owning client SHALL receive one snapshot containing all final values
- **AND** no intermediate reserve-credit or progress-only snapshot SHALL be sent

#### Scenario: No whole result is due
- **WHEN** a 100-tick settlement updates only transient fractional remainders
- **THEN** no profile snapshot SHALL be sent

### Requirement: Acceleration downgrade is a bounded runtime status
When spirit acceleration becomes unavailable, the server SHALL send one
clientbound status describing the transition to ordinary meditation. It SHALL
not resend that status every tick or expose an inventory/profile mutation path
to the client.

#### Scenario: Spirit resources run dry
- **WHEN** the authoritative session changes from spirit to normal mode
- **THEN** the owning client SHALL receive one downgrade transition
- **AND** the session SHALL continue without a changed-profile snapshot unless settlement also changed the profile

### Requirement: Advancement status is server-derived and clientbound-only
The server SHALL report accepted ordinary/bottleneck state, declared duration,
interruption/rejection reason, and completion result through the existing
clientbound runtime-status architecture. Status SHALL be sent at transitions,
not every tick, and SHALL not install or author a client profile. The only new
client input SHALL be the bounded start-breakthrough action added to the same
intent payload.

#### Scenario: Advancement starts
- **WHEN** the server accepts a valid current-stage rule
- **THEN** the owning client SHALL receive status derived from that rule's kind and duration
- **AND** no profile snapshot SHALL be sent merely for starting transient state

#### Scenario: Advancement completes
- **WHEN** one immutable transition profile commits
- **THEN** the owning client SHALL receive one final changed profile snapshot and completion status
- **AND** no target or result SHALL have originated from client data

### Requirement: Client advancement state remains a read-only cache
The client SHALL cache server status only for presentation and SHALL clear it
on disconnect. It SHALL not expose an API to complete a transition, change its
duration, choose a target, deduct stability, or replace a profile.

#### Scenario: The client disconnects during advancement
- **WHEN** client logout fires
- **THEN** cached advancement status SHALL clear before a later connection can observe it

### Requirement: H presents requirements and the release ceiling without control
The read-only H screen SHALL resolve and display current progress/cap,
advancement kind, duration, stability requirement/cost, current transient state,
and unsupported Qi-IV release ceiling when data is available. Missing registry
or status data SHALL render an unavailable marker. Opening or rendering H SHALL
send no start, stop, target, or profile payload.

#### Scenario: A full Qi-III profile is displayed
- **WHEN** the registered bottleneck rule and latest profile are available
- **THEN** H SHALL show the 200-tick bottleneck, 80 stability requirement, and 30 success cost

#### Scenario: Qi IV is displayed
- **WHEN** the current stage has no cap or advancement rule
- **THEN** H SHALL show the current release limit rather than imply a hidden random requirement

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
