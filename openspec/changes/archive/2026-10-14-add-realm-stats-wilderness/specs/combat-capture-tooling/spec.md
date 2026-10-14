## ADDED Requirements

### Requirement: Natural monster evidence is an explicit capture option
Capture sessions SHALL disable natural monsters by default and SHALL offer an explicit `--natural-monsters` option that enables the isolated server's monster spawning setting. The session state SHALL record the option, and stopping SHALL restore the prior server properties. The wilderness evidence script SHALL enable this option before claiming observed natural births; a game-rule change alone MUST NOT be treated as enabling a disabled server setting.

#### Scenario: Existing capture behavior
- **WHEN** a capture session is started without the option
- **THEN** natural monster spawning remains disabled

#### Scenario: Wilderness trial
- **WHEN** a wilderness evidence session explicitly enables natural monsters and its trial enables the spawning game rule
- **THEN** the normal server spawn path can produce entities, with observed births distinguished from administrator fixtures
