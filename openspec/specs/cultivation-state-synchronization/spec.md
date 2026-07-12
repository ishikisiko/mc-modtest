# cultivation-state-synchronization Specification

## Purpose

Define owning-client snapshot delivery, read-only client caching, payload
direction, mutation authority, and dedicated-server side safety.

## Requirements
### Requirement: Cultivation snapshots flow only from server to owning client
The mod SHALL register `CultivationSnapshotPayload` with payload id `myvillage:cultivation_snapshot` as a play-to-client payload. The payload SHALL carry an immutable cultivation profile snapshot or an equivalent read-only DTO containing the same v1 fields. The foundation MUST NOT register a client-to-server payload that writes realm, stage, root, progress, stability, power, learned techniques, or mastery.

#### Scenario: The server synchronizes one player
- **WHEN** `CultivationService#syncToClient` is called for a `ServerPlayer`
- **THEN** the server SHALL send the snapshot only to that owning player
- **AND** it SHALL NOT broadcast another player's private cultivation profile

#### Scenario: A client attempts to author cultivation state
- **WHEN** registered cultivation payload directions are inspected
- **THEN** no play-to-server cultivation mutation payload SHALL exist
- **AND** the server attachment SHALL remain the authoritative state

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
The server SHALL synchronize the current profile on player login, player respawn after true death or End return, player dimension change, successful administrator mutation, and profile reset. It SHALL NOT synchronize cultivation state every tick.

#### Scenario: An administrator changes a profile
- **WHEN** a cultivation command succeeds through `CultivationService`
- **THEN** the service SHALL send the newly installed snapshot immediately

#### Scenario: A cultivation mutation fails
- **WHEN** a cultivation command or service validation fails
- **THEN** the server SHALL leave the profile unchanged
- **AND** it SHALL NOT send a changed snapshot

#### Scenario: A player remains idle
- **WHEN** no lifecycle trigger or successful mutation occurs
- **THEN** the server SHALL NOT send periodic cultivation snapshots

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
The client SHALL register `Open Cultivation Profile` as a configurable key mapping that defaults to `H`. While a local player is present and no other screen is open, consuming that key SHALL open a non-pausing cultivation profile screen. Pressing the configured key again while that screen is focused or pressing Escape SHALL close it.

#### Scenario: A player opens the diagnostic profile
- **WHEN** the owning client has a synchronized snapshot and activates the profile key
- **THEN** the screen SHALL show player identity, translated realm and stage, raw cultivation progress, stability, raw current spiritual power, spiritual-root affinities or unawakened state, learned techniques with category, grade, and mastery, and profile schema version
- **AND** the screen SHALL read the immutable latest snapshot without sending a cultivation payload

#### Scenario: The snapshot has not arrived
- **WHEN** the profile screen opens before `ClientCultivationState` contains a snapshot
- **THEN** it SHALL show an explicit unavailable or waiting state
- **AND** it SHALL NOT fabricate the default profile

#### Scenario: A profile references unavailable definitions
- **WHEN** the screen cannot resolve a realm, stage, element, or technique id against the synchronized registries
- **THEN** it SHALL retain and display the raw id with an unavailable marker
- **AND** it SHALL continue rendering the remaining valid profile fields

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
`CultivationSnapshotPayload.STREAM_CODEC` SHALL encode and decode every v1 profile field and SHALL preserve syntactically valid unknown definition ids. Invalid profile invariants SHALL fail decoding rather than producing an unvalidated cache value.

#### Scenario: A non-default snapshot round-trips
- **WHEN** a snapshot containing a root, multiple techniques, nonzero progress, stability, power, and unknown ids is encoded to a registry-friendly buffer and decoded
- **THEN** the decoded snapshot SHALL equal the original

#### Scenario: An invalid profile is presented to the stream codec
- **WHEN** decoded payload data violates a profile numeric or root invariant
- **THEN** payload decoding SHALL fail
- **AND** the client cache SHALL retain its previous value
