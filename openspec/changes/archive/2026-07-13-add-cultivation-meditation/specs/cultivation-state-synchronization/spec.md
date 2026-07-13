## ADDED Requirements

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
