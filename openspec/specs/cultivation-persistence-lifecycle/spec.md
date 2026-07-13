# cultivation-persistence-lifecycle Specification

## Purpose

Define codec-backed player attachment persistence and the login, death,
respawn, End-return, dimension-change, and disconnect lifecycle contract.
## Requirements
### Requirement: Player cultivation data uses a codec-backed NeoForge Data Attachment
The mod SHALL register attachment type `myvillage:cultivation_profile` with a default `CultivationProfile`, `CultivationProfile.CODEC`, and `AttachmentType.Builder#copyOnDeath()`. The profile SHALL be stored on the player entity and SHALL NOT use per-player `SavedData`, legacy Forge Capability APIs, or an in-place mutable attachment object.

#### Scenario: A new player profile is requested
- **WHEN** `getData` is called for a player without serialized cultivation data
- **THEN** the attachment SHALL supply the valid v1 default profile

#### Scenario: A player is saved and reloaded
- **WHEN** a non-default cultivation profile is saved with the player and the world is restarted
- **THEN** the attachment codec SHALL restore an equal profile

### Requirement: Attachment replacement is the persistence write boundary
Every cultivation profile mutation SHALL create a validated new immutable profile and SHALL write it with `ServerPlayer#setData`. Code SHALL NOT mutate an object returned by `getData`, retain a mutable alias to attachment maps, or depend on implicit dirty detection after in-place changes.

#### Scenario: A profile field is changed
- **WHEN** a valid service mutator changes one profile field
- **THEN** it SHALL call `setData` with a new profile value
- **AND** the previous profile and its nested values SHALL remain unchanged

#### Scenario: Validation fails before replacement
- **WHEN** a proposed profile violates an invariant or required registry reference
- **THEN** the service SHALL leave the existing attachment value installed

### Requirement: copyOnDeath is the sole profile copy mechanism
The attachment's `copyOnDeath` behavior SHALL be the only mechanism that copies cultivation data from an old player entity to a replacement player entity. The mod MUST NOT also copy the cultivation profile in `PlayerEvent.Clone` or another manual clone handler.

#### Scenario: A player truly dies
- **WHEN** NeoForge replaces a player entity after true death
- **THEN** the attachment system SHALL copy the complete cultivation profile to the replacement player
- **AND** the mod SHALL NOT apply progress, power, stability, root, or mastery loss

#### Scenario: A duplicate clone implementation is inspected
- **WHEN** cultivation lifecycle registration is validated
- **THEN** it SHALL contain the `copyOnDeath` attachment configuration
- **AND** it SHALL NOT contain a manual cultivation profile copy in `PlayerEvent.Clone`

### Requirement: End return preserves the profile without duplicate copying
Returning from the End through the exit portal SHALL preserve the serializable cultivation attachment using NeoForge's normal player replacement behavior. The mod SHALL NOT perform an additional death copy, merge, reset, or penalty when the clone is not caused by death.

#### Scenario: A player returns from the End
- **WHEN** the player entity is replaced for End return rather than true death
- **THEN** the replacement player SHALL retain a profile equal to the original
- **AND** exactly one attachment-copy mechanism SHALL own that transfer

#### Scenario: End return is followed by synchronization
- **WHEN** the post-End replacement player reaches the respawn lifecycle event
- **THEN** the server SHALL synchronize the already-preserved profile
- **AND** synchronization SHALL NOT copy or mutate the profile again

### Requirement: Lifecycle events synchronize only after authoritative state is available
`CultivationEvents` SHALL handle server-player login, respawn, and dimension change as synchronization triggers. Lifecycle listeners SHALL read the attachment through `CultivationService` and SHALL NOT bypass the service to mutate it. Client disconnect cleanup SHALL be handled only by a physical-client event listener.

#### Scenario: A player logs in
- **WHEN** `PlayerLoggedInEvent` fires for a `ServerPlayer`
- **THEN** the server SHALL send that player's current or default profile snapshot

#### Scenario: A player respawns
- **WHEN** `PlayerRespawnEvent` fires after true death or End return
- **THEN** the server SHALL send the profile attached to the replacement player

#### Scenario: A player changes dimensions
- **WHEN** `PlayerChangedDimensionEvent` fires for a `ServerPlayer`
- **THEN** the server SHALL send the current profile after the dimension transition

#### Scenario: A client disconnects
- **WHEN** `ClientPlayerNetworkEvent.LoggingOut` fires on the client
- **THEN** the client cultivation snapshot cache SHALL be cleared

### Requirement: The foundation persists no transient cultivation runtime state
The cultivation attachment SHALL contain only the v1 persistent profile fields. It SHALL NOT create placeholder casting, cooldown, meditation-session, recovery-timer, temporary buff, or technique-execution state.

#### Scenario: A profile is serialized
- **WHEN** the attachment codec writes a v1 profile
- **THEN** the serialized value SHALL contain only the defined persistent v1 fields
- **AND** it SHALL NOT contain empty-shell runtime fields for future gameplay

### Requirement: Initiation results use the existing attachment lifecycle
Generated spiritual roots, the `myvillage:mortal_qi_sensed` stage, and inherited `TechniqueProgress` SHALL persist solely as existing v1 `CultivationProfile` fields in the codec-backed `myvillage:cultivation_profile` attachment. The implementation SHALL NOT add block-local player data, per-player `SavedData`, legacy Capability storage, a second attachment, or a cultivation `PlayerEvent.Clone` copy path.

#### Scenario: An initiated player relogs or restarts the server
- **WHEN** a profile containing a generated root and learned `myvillage:basic_breathing` is saved and loaded
- **THEN** every affinity and the mastery value SHALL be restored exactly

#### Scenario: An initiated player dies or returns from the End
- **WHEN** NeoForge replaces the player after true death or End return
- **THEN** the existing attachment copy behavior SHALL preserve the complete initiated profile
- **AND** no ritual-specific clone, merge, reroll, reset, or mastery rewrite SHALL run

#### Scenario: An initiated player changes dimensions
- **WHEN** the player changes dimensions
- **THEN** the same persisted root and learned-technique values SHALL remain authoritative
- **AND** the lifecycle event SHALL synchronize rather than regenerate them

### Requirement: Datapack changes do not rewrite persisted initiation data
Registry reload or later datapack changes SHALL NOT automatically regenerate an existing spiritual root, remove unknown element or technique ids, or rewrite technique mastery. Reset SHALL remain the explicit administrator route that clears the profile before a later rules-based awakening or inheritance.

#### Scenario: A saved element definition is removed
- **WHEN** a persisted root references an element absent from the current registry
- **THEN** loading and saving the v1 profile SHALL preserve that id and affinity exactly

#### Scenario: Awakening weights change after save
- **WHEN** the current datapack changes an element's `awakening_weight`
- **THEN** already persisted roots SHALL remain unchanged
- **AND** the new weights SHALL affect only later generation after a root is absent
