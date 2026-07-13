## MODIFIED Requirements

### Requirement: Awakening validates state and commits root plus stage atomically
`SpiritualRootAwakeningService` SHALL read the current profile and registries,
obtain the Overworld seed, invoke the pure generator, and submit one immutable
replacement through `CultivationService`. Ordinary awakening SHALL require no
existing root, realm `myvillage:mortal`, and stage
`myvillage:mortal_unawakened` or `myvillage:mortal_qi_sensed`. Success SHALL set
the generated root and sensed stage in one replacement while preserving current
schema version, realm, cultivation progress, stability, spiritual power,
lifespan consumed, meditation reserve, and learned techniques.

#### Scenario: A default profile awakens
- **WHEN** an unawakened default mortal v2 profile uses the service with eligible elements
- **THEN** the final profile SHALL contain the generated root and sensed stage
- **AND** exactly one attachment replacement and one final client snapshot SHALL occur

#### Scenario: An administrator cleared a sensed mortal root
- **WHEN** a sensed mortal v2 profile has no root after `clearroot`
- **THEN** awakening SHALL restore the root while preserving nonzero lifespan and reserve

#### Scenario: A non-mortal profile lacks a root
- **WHEN** a rootless profile's realm is not mortal
- **THEN** awakening SHALL return `INVALID_PROFILE_STATE` without forcing a realm or changing either v2 counter

#### Scenario: The update boundary rejects the replacement
- **WHEN** `CultivationService` rejects the proposed awakened profile
- **THEN** no intermediate profile, changed counter, or changed snapshot SHALL be observable

### Requirement: Initiation does not implement cultivation execution or progression
Awakening and inheritance actions SHALL remain acquisition-only: neither action
SHALL start meditation, execute Basic Breathing, recover power, grant
cultivation/stability/mastery, advance a realm/stage, or perform a breakthrough.
The initiation capability MAY coexist with separately specified profile-v2,
lifespan, meditation, gain, and advancement capabilities, but the two ritual
actions SHALL not bypass them.

#### Scenario: A player completes both steles under schema v2
- **WHEN** a player awakens and inherits `myvillage:basic_breathing`
- **THEN** the ritual SHALL not itself change progress, power, stability, lifespan, reserve, mastery, attributes, or effects
- **AND** it SHALL preserve the current v2 counters while adding only the declared root/stage and technique results
