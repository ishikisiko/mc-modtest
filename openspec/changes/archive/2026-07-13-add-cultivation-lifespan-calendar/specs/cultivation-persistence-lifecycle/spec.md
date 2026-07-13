## MODIFIED Requirements

### Requirement: Player cultivation data uses a codec-backed NeoForge Data Attachment
The mod SHALL register attachment type `myvillage:cultivation_profile` with a
default current-v2 `CultivationProfile`, the version-dispatched
`CultivationProfile.CODEC`, and `AttachmentType.Builder#copyOnDeath()`. The
profile SHALL be stored on the player entity and SHALL NOT use per-player
`SavedData`, legacy Forge Capability APIs, or an in-place mutable attachment
object.

#### Scenario: A new player profile is requested
- **WHEN** `getData` is called for a player without serialized cultivation data
- **THEN** the attachment SHALL supply the valid v2 default profile

#### Scenario: A v1 player is loaded
- **WHEN** the attachment codec reads a valid serialized v1 profile
- **THEN** it SHALL expose the explicitly migrated current-v2 value

#### Scenario: A player is saved and reloaded
- **WHEN** a non-default v2 cultivation profile is saved with the player and the world is restarted
- **THEN** the attachment codec SHALL restore an equal v2 profile

### Requirement: The foundation persists no transient cultivation runtime state
The cultivation attachment SHALL contain only current persistent v2 profile
fields. It SHALL NOT persist calendar ticks, warning-delivery flags,
lifespan-batch timers, meditation sessions, casting, cooldown, recovery timers,
temporary buffs, or technique-execution remainders.

#### Scenario: A v2 profile is serialized
- **WHEN** the attachment codec writes the current profile
- **THEN** it SHALL include the defined persistent v2 fields
- **AND** it SHALL contain none of the declared transient or world-shared fields

## ADDED Requirements

### Requirement: Pending lifespan uses the existing attachment owner
Pending lifespan SHALL be committed as checked immutable v2 replacements
through `CultivationService` every 600 ticks and at required lifecycle
boundaries. `copyOnDeath` SHALL remain the sole player-copy owner; no lifespan
clone handler, second attachment, or per-player `SavedData` SHALL be added.

#### Scenario: True death replaces the player
- **WHEN** a living player has committed and pending lifespan ticks at death
- **THEN** pending ticks SHALL be applied exactly once around replacement
- **AND** `copyOnDeath` SHALL preserve the resulting complete v2 profile exactly once

#### Scenario: End return replaces the player
- **WHEN** an End-return replacement occurs
- **THEN** lifespan and reserve SHALL be preserved without duplicate copy or addition
