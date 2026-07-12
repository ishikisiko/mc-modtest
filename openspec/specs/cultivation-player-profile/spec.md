# cultivation-player-profile Specification

## Purpose

Define the immutable version-1 cultivation profile, spiritual-root and
technique-progress values, invariants, defaults, and unknown-id behavior.

## Requirements
### Requirement: The cultivation profile has a stable immutable v1 schema
The system SHALL represent a player's cultivation state as an immutable `CultivationProfile` value with `schemaVersion`, `realmId`, `stageId`, `cultivationProgress`, `stability`, `currentSpiritualPower`, optional `spiritualRoot`, and a `learnedTechniques` map. The current schema version SHALL be `1`. The schema SHALL NOT persist a redundant awakened flag or any technique equipment slot.

#### Scenario: A new profile is created
- **WHEN** a player has no stored cultivation attachment
- **THEN** the system SHALL create a version-1 profile with realm `myvillage:mortal`, stage `myvillage:mortal_unawakened`, zero progress, zero stability, zero current spiritual power, no spiritual root, and no learned techniques

#### Scenario: Awakening state is read
- **WHEN** code needs to determine whether a player is awakened
- **THEN** the system SHALL derive the result from whether `spiritualRoot` is present
- **AND** the serialized profile SHALL NOT contain a separate awakened boolean

#### Scenario: A v1 profile is round-tripped
- **WHEN** a valid profile containing a root and learned techniques is encoded and decoded with `CultivationProfile.CODEC`
- **THEN** every v1 field and value SHALL equal the original profile

### Requirement: Profile identifiers remain stable and definition-independent
The profile SHALL encode realm, stage, spiritual-element, and technique identifiers as `ResourceLocation` values. Profile persistence SHALL NOT encode Java enum ordinals, registry numeric ids, holders, or resolved definition objects. The profile codec SHALL validate identifier syntax without requiring the referenced definition to exist in the current registry.

#### Scenario: A profile references removed definitions
- **WHEN** a syntactically valid profile contains realm, stage, element, or technique ids absent from the current datapack registries
- **THEN** `CultivationProfile.CODEC` SHALL decode the profile successfully
- **AND** re-encoding SHALL preserve the unknown ids exactly

#### Scenario: An invalid identifier is decoded
- **WHEN** persisted profile data contains a string that is not a valid `ResourceLocation`
- **THEN** the profile codec SHALL return a controlled decode error

### Requirement: Profile numeric invariants are enforced at every construction boundary
`cultivationProgress`, `currentSpiritualPower`, and every technique mastery value SHALL be non-negative `long` values. `stability` SHALL be an integer from `0` through `100` inclusive. Profile factories, update methods, and codecs SHALL enforce the same bounds.

#### Scenario: Negative progression data is rejected
- **WHEN** code or serialized input supplies negative cultivation progress, current spiritual power, or technique mastery
- **THEN** construction or decoding SHALL fail with a specific validation error

#### Scenario: Stability is out of range
- **WHEN** code or serialized input supplies stability below `0` or above `100`
- **THEN** construction or decoding SHALL fail

#### Scenario: Boundary values are accepted
- **WHEN** a profile uses stability `0` or `100` and non-negative long values
- **THEN** the profile SHALL be valid

### Requirement: Spiritual roots use a generic validated affinity map
`SpiritualRoot` SHALL contain an immutable map from element `ResourceLocation` to integer basis points. Every value SHALL be from `0` through `10000` inclusive, and the sum for a present root SHALL equal exactly `10000`. The model SHALL support any number of registered elements and SHALL NOT encode a fixed five-element enum.

#### Scenario: A valid generic root is created
- **WHEN** a root contains registered or syntactically valid element ids whose basis points are individually in range and total `10000`
- **THEN** the root SHALL be accepted regardless of how many element ids it contains

#### Scenario: Root affinity total is invalid
- **WHEN** the affinity values total any value other than `10000`
- **THEN** root construction and decoding SHALL fail

#### Scenario: One root affinity is out of range
- **WHEN** any root affinity is below `0` or above `10000`
- **THEN** root construction and decoding SHALL fail even when the arithmetic total is `10000`

#### Scenario: A caller mutates its source map
- **WHEN** a caller changes the map used to construct a valid root
- **THEN** the previously created root SHALL remain unchanged

### Requirement: Learned technique progress is immutable and minimal
The learned-technique map SHALL associate each technique `ResourceLocation` with an immutable `TechniqueProgress` containing only non-negative `masteryPoints`. The map SHALL be defensively copied and SHALL NOT contain equipment, cooldown, cost, executor, damage, projectile, buff, or effect-script state.

#### Scenario: A technique is learned and mastered
- **WHEN** the service learns a technique and later sets its mastery
- **THEN** the profile SHALL contain a new technique entry with the requested non-negative mastery
- **AND** the pre-update profile instance SHALL remain unchanged

#### Scenario: A technique is forgotten
- **WHEN** the service forgets a learned technique
- **THEN** the new profile SHALL omit that technique
- **AND** all unrelated profile fields and technique entries SHALL remain unchanged

### Requirement: CultivationService is the only server-side mutation boundary
The system SHALL expose `getProfile`, `replaceProfile` or `updateProfile`, `resetProfile`, `setRealmAndStage`, `setProgress`, `setStability`, `setSpiritualPower`, `setSpiritualRoot`, `clearSpiritualRoot`, `learnTechnique`, `forgetTechnique`, `setTechniqueMastery`, and `syncToClient` through `CultivationService`. Commands and lifecycle events MUST NOT mutate the cultivation attachment directly. Every successful mutator SHALL construct a validated new profile, call `ServerPlayer#setData`, and trigger synchronization; every failed mutator SHALL leave the old attachment unchanged and return a controlled failure.

#### Scenario: A valid update succeeds
- **WHEN** a service operation receives valid input and current registry references
- **THEN** it SHALL replace the attachment with a newly constructed profile
- **AND** it SHALL synchronize the new profile to the owning client

#### Scenario: An invalid update fails atomically
- **WHEN** a service operation receives an invalid number, root, realm-stage pair, or definition id
- **THEN** it SHALL report the specific failure
- **AND** it SHALL NOT call `setData`
- **AND** it SHALL NOT synchronize a changed snapshot

#### Scenario: Mastery is set for an unlearned technique
- **WHEN** `setTechniqueMastery` targets a registered technique that is not in the learned-technique map
- **THEN** the service SHALL reject the operation rather than learning the technique implicitly

### Requirement: Profile schema changes use explicit migrations
The v1 decoder SHALL accept schema version `1` and SHALL reject an unsupported schema version with a controlled codec error. A future schema-changing implementation MUST retain version-specific old decoders and MUST migrate decoded old values through explicit, validated `vN` to `vN+1` transformations before writing the current schema.

#### Scenario: An unsupported schema is loaded by v1 code
- **WHEN** a persisted profile declares a schema version other than `1`
- **THEN** the v1 codec SHALL fail explicitly rather than guessing fields or silently resetting the profile

#### Scenario: A future equipment-slot schema is introduced
- **WHEN** a later change adds technique equipment slots or another persisted field
- **THEN** that change MUST add migration coverage from v1
- **AND** it MUST NOT reinterpret or mutate the existing v1 field meanings
