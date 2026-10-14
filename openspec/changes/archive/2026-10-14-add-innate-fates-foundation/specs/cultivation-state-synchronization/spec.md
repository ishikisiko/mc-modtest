## MODIFIED Requirements

### Requirement: Cultivation snapshots flow only from server to owning client
The mod SHALL keep `myvillage:cultivation_snapshot` play-to-client and carry the immutable current-v4 cultivation profile, the independent immutable fate state and the server fate point budget. Time and runtime-status payloads SHALL remain clientbound-only. Cultivation serverbound inputs SHALL be limited to the bounded meditation action, a core-technique switch ID and a fate intent carrying action, seen draft revision and a bounded slot or six-bit selection mask. None SHALL carry client-authored realm, stage, root, affinity, progress, stability, power, lifespan, effect value, cost, rate or result. The server SHALL resolve and validate all replacements.

#### Scenario: The server synchronizes one player
- **WHEN** `CultivationService#syncToClient` is called for a `ServerPlayer`
- **THEN** the current profile, fate state and point budget SHALL be sent only to that owning player
- **AND** another player's private profile SHALL not be broadcast

#### Scenario: A client submits a cultivation action
- **WHEN** registered payload directions and fields are inspected
- **THEN** only the bounded meditation action, core switch ID and bounded fate selection intent SHALL be registered as cultivation serverbound inputs
- **AND** the server attachment, candidate eligibility and resolved gameplay numbers SHALL remain authoritative
