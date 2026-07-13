## MODIFIED Requirements

### Requirement: Snapshot synchronization occurs at authoritative lifecycle and mutation points
The server SHALL synchronize the current profile on player login, player respawn after true death or End return, player dimension change, successful administrator mutation, successful spiritual-root awakening, and successful basic-technique inheritance. Each successful initiation action SHALL send only the one final installed profile, and the server SHALL NOT synchronize a root-only/stage-only intermediate value or cultivation state every tick.

#### Scenario: An administrator changes a profile
- **WHEN** a cultivation command succeeds through `CultivationService`
- **THEN** the service SHALL send the newly installed snapshot immediately

#### Scenario: A stele ritual succeeds
- **WHEN** awakening or inheritance commits a valid replacement through `CultivationService`
- **THEN** the service SHALL send exactly one snapshot containing the final profile to the owning player

#### Scenario: A cultivation mutation fails
- **WHEN** a cultivation command, awakening service, inheritance service, or service validation fails
- **THEN** the server SHALL leave the profile unchanged
- **AND** it SHALL NOT send a changed snapshot

#### Scenario: A player remains idle
- **WHEN** no lifecycle trigger or successful mutation occurs
- **THEN** the server SHALL NOT send periodic cultivation snapshots

## ADDED Requirements

### Requirement: Initiation adds no client-authoritative mutation path
The initiation change SHALL reuse the existing `CultivationSnapshotPayload` profile shape and clientbound registration. It MUST NOT add an awaken, root-generation, affinity-selection, inheritance, or technique-learning play-to-server payload. Client code SHALL NOT receive the Overworld seed or expose an API that selects or installs a root or technique.

#### Scenario: Payload registration is inspected after initiation integration
- **WHEN** the registered payload types and directions are enumerated
- **THEN** `myvillage:cultivation_snapshot` SHALL remain clientbound-only
- **AND** no cultivation play-to-server mutation payload SHALL exist
- **AND** the flying-sword input payload SHALL remain the existing independent serverbound payload

### Requirement: The H profile screen remains a read-only ritual result view
The existing profile screen SHALL render the final synchronized stage, spiritual-root affinities, learned `myvillage:basic_breathing`, and mastery `0` without adding awaken, reroll, learn, equip, execute, meditation, or other mutation controls. The existing sharp-content/background-blur ordering SHALL remain unchanged.

#### Scenario: A player views each ritual phase
- **WHEN** the client opens the H screen before awakening, after awakening, and after inheritance
- **THEN** it SHALL respectively show unawakened/empty, `mortal_qi_sensed` with affinities/empty, and `mortal_qi_sensed` with affinities/basic-breathing mastery `0`
- **AND** no view SHALL send a cultivation payload

#### Scenario: Initiation UI regression is checked
- **WHEN** the H screen is manually inspected after the change
- **THEN** its profile content SHALL remain sharp and readable
- **AND** any unobserved visual behavior SHALL be recorded as `not_verified` rather than inferred from build success
