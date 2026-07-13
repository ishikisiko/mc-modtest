# cultivation-player-profile Specification

## Purpose

Define the immutable version-1 cultivation profile, spiritual-root and
technique-progress values, invariants, defaults, and unknown-id behavior.
## Requirements
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
The retained v1 decoder SHALL accept only schema version `1`, the retained v2
decoder SHALL accept only schema version `2`, and the current v3 decoder SHALL
accept only schema version `3`. The current codec SHALL dispatch by declared
version and migrate through explicit validated transformations before writing
v3. Future schema changes MUST retain all supported old decoders and MUST NOT
reinterpret existing v1, v2, or v3 field meanings.

#### Scenario: An unsupported schema is loaded by v3 code
- **WHEN** a persisted profile declares a schema version other than `1`, `2`, or `3`
- **THEN** the codec SHALL fail explicitly rather than guess fields or silently reset the profile

#### Scenario: A future schema is introduced
- **WHEN** a later change adds another persisted field
- **THEN** it MUST add a validated migration from v3 and retain both earlier migration paths
- **AND** it MUST preserve the meanings of spiritual affinity and legacy reserve

### Requirement: Ritual services submit one validated immutable replacement
Awakening and inheritance services SHALL construct a complete immutable replacement and call `CultivationService#replaceProfile`, `updateProfile`, or an equivalently validating extension exactly once per successful ritual action. Blocks and command handlers MUST NOT call `ServerPlayer#setData`, mutate a retrieved profile or nested map, or compose the action from multiple service mutations. A failed or repeated action SHALL leave the previous profile value equal and SHALL NOT synchronize a changed snapshot.

#### Scenario: Root and stage are installed together
- **WHEN** awakening succeeds
- **THEN** one replacement SHALL contain both the generated root and `myvillage:mortal_qi_sensed`
- **AND** no root-only or stage-only intermediate profile SHALL be installed

#### Scenario: Basic breathing is inherited
- **WHEN** inheritance succeeds
- **THEN** one replacement SHALL add `myvillage:basic_breathing` at mastery `0`
- **AND** every unrelated field and learned-technique entry SHALL remain unchanged

#### Scenario: A repeat action is rejected
- **WHEN** an already awakened or already initiated profile repeats the corresponding action
- **THEN** the prior immutable profile and nested maps SHALL remain unchanged
- **AND** existing technique mastery SHALL NOT be reset

### Requirement: Cultivation progress is current-stage progress
`cultivationProgress` SHALL represent progress within the profile's current
realm stage, not lifetime cultivation earned. Ordinary gameplay settlement
SHALL apply no progress above the current registered stage cap and SHALL carry
no excess into a later stage. Migration and privileged administrator data MAY
retain a raw value above the cap; settlement SHALL treat such a value as capped
without normalizing it.

#### Scenario: Normal settlement reaches the current cap
- **WHEN** a player's applicable progress gain is greater than the remaining current-stage capacity
- **THEN** the replacement profile SHALL contain exactly the cap
- **AND** no progress SHALL be banked for a later advancement

#### Scenario: Existing raw progress is above the cap
- **WHEN** a migrated or administrator-created profile has progress above its current stage cap
- **THEN** settlement SHALL preserve the raw value and add no further progress

### Requirement: Meditation reserve is a separate durable balance
`meditationQiReserve` SHALL remain a non-negative persistent meditation-only
balance. It SHALL NOT be read as, converted to, or substituted for
`currentSpiritualPower`; meditation gain SHALL NOT spend or restore current
spiritual power. Every successful profile replacement and interruption SHALL
preserve unspent reserve exactly.

#### Scenario: Spirit meditation spends reserve
- **WHEN** an applicable spirit bonus point is settled
- **THEN** reserve SHALL decrease by exactly one for that bonus point
- **AND** current spiritual power SHALL remain unchanged

#### Scenario: Meditation stops with reserve remaining
- **WHEN** a session is interrupted or stopped after a prior stone conversion
- **THEN** the unspent reserve SHALL remain in the persistent profile

### Requirement: A gain settlement installs one immutable profile replacement
A successful cultivation settlement SHALL compute progress, stability, Basic
Breathing mastery, and reserve as one proposed immutable profile and submit it
through `CultivationService` once. Runtime managers, payload handlers, and
inventory helpers MUST NOT mutate the attachment or a retrieved profile/map
directly. A rejected settlement SHALL install and synchronize nothing.

#### Scenario: Several fields become due together
- **WHEN** one settlement produces progress, stability, mastery, and reserve spend
- **THEN** one validated replacement SHALL contain all final values
- **AND** no intermediate profile SHALL be installed

#### Scenario: Settlement validation fails
- **WHEN** the proposed gain profile violates a profile or registry invariant
- **THEN** the previous immutable profile SHALL remain installed
- **AND** no changed profile snapshot SHALL be sent

### Requirement: Advancement preserves v2 profile identity outside transition fields
A successful advancement SHALL construct one immutable v2 replacement that
changes only realm id, stage id, stage-local progress to zero, and stability by
the declared success cost. It SHALL preserve `lifespanConsumedTicks`,
`meditationQiReserve`, current spiritual power, spiritual root, learned
techniques/mastery, schema version, and every unrelated field. It SHALL use
`CultivationService` as the sole attachment mutation boundary.

#### Scenario: Success preserves durable counters and learning
- **WHEN** a player with nonzero lifespan, reserve, spiritual power, affinities, and mastery advances
- **THEN** all those values SHALL equal their pre-advancement values
- **AND** only transition-owned fields SHALL change

#### Scenario: A transition replacement is invalid
- **WHEN** target references or calculated stability fail profile validation
- **THEN** the previous profile SHALL remain installed and no changed snapshot SHALL be sent

### Requirement: Bottleneck interruption is one atomic stability-only replacement
An eligible player/world interruption of the Qi-III bottleneck SHALL submit at
most one immutable replacement through `CultivationService` that changes only
stability by exact loss `5`, clamped at zero. Ordinary or administrative
teardown SHALL submit no penalty replacement. No interruption SHALL reset
progress or spend reserve.

#### Scenario: A full-progress Qi-III bottleneck is interrupted
- **WHEN** its current stability is 80 and a penalized interruption occurs
- **THEN** one replacement SHALL retain Qi III and progress 1200 with stability 75
- **AND** lifespan, reserve, power, root, and techniques SHALL remain unchanged

#### Scenario: Several hooks observe the same interruption
- **WHEN** interruption handling is invoked repeatedly after session removal
- **THEN** later invocations SHALL install no additional profile replacement

### Requirement: The cultivation profile has a stable immutable v3 schema
The current immutable `CultivationProfile` SHALL contain every v2 field plus a
non-negative integer `spiritualAffinity`. Current schema version SHALL be `3`.
A new or reset profile SHALL use affinity `10`, preserve the previous default
realm/stage/root/technique values, and use zero for every prior numeric field.
The profile SHALL retain `meditationQiReserve` as inert compatibility data but
SHALL NOT add a client-writable, derived-speed, or meditation-mode field.
Stored stability SHALL be a non-negative integer without a fixed schema-level
upper bound because its gameplay cap is derived from the current stage.

#### Scenario: A new v3 profile is created
- **WHEN** a player has no stored cultivation attachment
- **THEN** the default profile SHALL use schema `3` and spiritual affinity `10`
- **AND** its realm, stage, root, techniques, and prior numeric defaults SHALL match the v2 default

#### Scenario: A non-default v3 profile round-trips
- **WHEN** the current codec encodes and decodes a valid profile with non-default affinity and reserve
- **THEN** every v3 field and unknown syntactically valid id SHALL equal the original

#### Scenario: Negative affinity is supplied
- **WHEN** a constructor, codec, snapshot, or service replacement receives spiritual affinity below zero
- **THEN** validation SHALL fail without installing or synchronizing a replacement

#### Scenario: Stability exceeds the historical fixed ceiling
- **WHEN** a valid v3 profile contains stability `500`
- **THEN** profile construction and codec round trip SHALL preserve it
- **AND** current-stage settlement and advancement rules SHALL enforce the applicable derived cap

### Requirement: V1 and v2 profiles migrate explicitly and losslessly to v3
The codec SHALL retain version-specific v1, v2, and v3 decoders. V1 SHALL
migrate through the retained validated v1-to-v2 transformation and then the
v2-to-v3 transformation; v2 SHALL migrate directly through that same final
transformation. Both old versions SHALL receive affinity `10`. All old fields,
unknown syntactically valid ids, over-cap progress, lifespan, and reserve SHALL
be preserved exactly. The current encoder SHALL write only version `3`, and any
other version SHALL fail explicitly.

#### Scenario: A non-default v1 profile is loaded
- **WHEN** valid v1 data contains progress, stability, power, root affinities, techniques, mastery, and unknown ids
- **THEN** the resulting v3 profile SHALL preserve every v1 value exactly and assign affinity `10`
- **AND** the retained v1-to-v2 defaults for lifespan and reserve SHALL remain zero

#### Scenario: A non-default v2 profile is loaded
- **WHEN** valid v2 data contains nonzero lifespan, reserve, and over-cap progress
- **THEN** the resulting v3 profile SHALL preserve those values exactly and assign affinity `10`

#### Scenario: An unsupported profile version is loaded
- **WHEN** persisted data declares a schema version other than `1`, `2`, or `3`
- **THEN** decoding SHALL fail with a controlled unsupported-version error rather than guess or reset

### Requirement: Every authoritative replacement preserves v3 fields atomically
Commands and every gameplay/lifecycle mutation SHALL submit complete
immutable replacements through `CultivationService`; initiation, lifespan
flushes, normal and spirit settlements, advancement, reset helpers, and
lifecycle handlers SHALL not bypass it. Unless an operation explicitly resets
the whole profile, it SHALL preserve `spiritualAffinity` and the legacy reserve
exactly. Client input SHALL NOT supply affinity or directly install a profile.

#### Scenario: Initiation or advancement replaces a v3 profile
- **WHEN** a valid ritual or advancement commits on a profile with non-default affinity and reserve
- **THEN** one final replacement SHALL preserve both values exactly
- **AND** no intermediate or client-authored profile SHALL be installed

#### Scenario: A profile mutation fails validation
- **WHEN** any proposed replacement violates a v3 invariant
- **THEN** `CultivationService` SHALL leave the old attachment equal and send no changed snapshot
