## ADDED Requirements

### Requirement: Heavy alchemy quality checks run remotely
The runner SHALL provide fixed-commit presets for the complete release gate and alchemy-quality acceptance, including clean stage1, mapped one-client brewing/use/restart/layout evidence and a two-client attribution check. Each run SHALL retain bounded resources, command timings, artifacts and confirmed cleanup. Failure SHALL NOT trigger a local heavy-test fallback.

#### Scenario: Cloud validation completes
- **WHEN** the remote release and alchemy-quality presets pass
- **THEN** their jar and evidence SHALL be downloaded and verified against the recorded source commit before delivery

#### Scenario: Cloud execution fails
- **WHEN** a remote job fails or cannot allocate
- **THEN** the failure SHALL remain explicit and no local Gradle or Minecraft job SHALL start automatically
