## ADDED Requirements

### Requirement: Remote capture keeps evidence and world isolation boundaries
A Modal client/capture run SHALL reuse the repository's mapped-input evidence workflow without DevBridge and SHALL keep all world state in an ephemeral run workspace, outside the persistent dependency cache. Remote client capture SHALL be advertised as experimental until validated in a real authenticated Sandbox. Software-rendered output SHALL remain developer evidence and SHALL NOT establish owner physical-client sound, FPS or look/feel acceptance.

#### Scenario: Remote client startup succeeds without mapped actions
- **WHEN** a remote Minecraft client reaches readiness but mapped capture has not been exercised
- **THEN** the ledger SHALL record startup only and leave interaction/capture unverified

#### Scenario: A run finishes after modifying a capture world
- **WHEN** remote capture cleanup terminates its Sandbox
- **THEN** that run's world SHALL NOT be retained in the dependency cache or reused as a later run's starting world
