## ADDED Requirements

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
