## MODIFIED Requirements

### Requirement: Cultivation snapshots flow only from server to owning client
The mod SHALL keep `myvillage:cultivation_snapshot` play-to-client and carry the immutable current-v4 cultivation profile, the independent immutable fate state and the server fate point budget. Time and runtime-status payloads SHALL remain clientbound-only. Cultivation serverbound inputs SHALL be limited to the bounded meditation action, a core-technique switch ID and a fate intent carrying action, seen draft revision, a bounded slot or six-bit selection mask and a server-issued session token, plus a close intent carrying only that token. None SHALL carry client-authored realm, stage, root, affinity, progress, stability, power, lifespan, effect value, cost, rate or result. The server SHALL resolve and validate all replacements.

#### Scenario: The server synchronizes one player
- **WHEN** `CultivationService#syncToClient` is called for a `ServerPlayer`
- **THEN** the current profile, fate state and point budget SHALL be sent only to that owning player
- **AND** another player's private profile SHALL not be broadcast

#### Scenario: A client submits a cultivation action
- **WHEN** registered payload directions and fields are inspected
- **THEN** only the bounded meditation action, core switch ID, bounded fate selection intent and session close token SHALL be registered as cultivation serverbound inputs
- **AND** the server attachment, candidate eligibility and resolved gameplay numbers SHALL remain authoritative


### Requirement: Initiation adds no client-authoritative mutation path
The initiation change SHALL reuse the existing `CultivationSnapshotPayload` profile shape and clientbound registration. It MUST NOT add an awaken, root-generation, affinity-selection, inheritance, or technique-learning play-to-server payload. Client code SHALL NOT receive the Overworld seed or expose an API that selects or installs a root or technique.

#### Scenario: Payload registration is inspected after initiation integration
- **WHEN** the registered payload types and directions are enumerated
- **THEN** `myvillage:cultivation_snapshot` SHALL remain clientbound-only
- **AND** separately specified bounded meditation/core-switch/fate intents and session closure SHALL remain the only cultivation serverbound inputs, with no client-authored root or inheritance result
- **AND** the flying-sword input payload SHALL remain the existing independent serverbound payload


## ADDED Requirements

### Requirement: Awakening opening is server-authorized and side safe
A bounded clientbound awakening-open notification carrying the server anchor block position and strictly positive session token SHALL be sent only to the player whose valid testing-stele interaction established a live server session. The owning cultivation snapshot SHALL provide the root, fate draft/result and server budget; the opening notification SHALL not duplicate definition registries or reveal the world seed. Client handling SHALL enqueue work behind a physical-client-only bridge. Session closure SHALL carry only the server-issued token as bounded close intent, never a client-authored anchor, authority, root or effect. Common payload and server code SHALL remain dedicated-server safe.

#### Scenario: Stele interaction opens the client screen
- **WHEN** the server accepts a testing-stele interaction for an awakened living player
- **THEN** it SHALL send current synchronized state before the clientbound opening notification to that player only

#### Scenario: A client closes the awakening screen
- **WHEN** the player leaves the awakening screen
- **THEN** a bounded close intent SHALL invalidate their server session without mutating the root or fate draft

#### Scenario: Dedicated server registers the new notification
- **WHEN** all cultivation payloads register on a dedicated server
- **THEN** registration SHALL load no Minecraft client class and SHALL preserve existing payload directions and bounded fate intent fields
