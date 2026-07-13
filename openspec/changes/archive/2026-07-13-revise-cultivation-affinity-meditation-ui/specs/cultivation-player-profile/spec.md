## REMOVED Requirements

### Requirement: The cultivation profile has a stable immutable v2 schema
**Reason**: Spiritual affinity is durable player state and requires schema v3;
v2 is retained only as an explicit decode/migration shape.

**Migration**: Decode valid v2 profiles exactly, preserve every v2 field
including `meditationQiReserve`, assign `spiritualAffinity` value `10`, validate
the resulting v3 profile, and encode only v3.

### Requirement: V1 profiles migrate explicitly and losslessly to v2
**Reason**: V3 is now the current write shape, so stopping at v2 is no longer a
complete migration.

**Migration**: Retain the exact v1-to-v2 transformation and continue through
the v2-to-v3 transformation with affinity `10`.

### Requirement: V2 counter invariants apply at every construction boundary
**Reason**: The current construction boundary is v3 and also validates spiritual
affinity.

**Migration**: Preserve the non-negative stability/lifespan/reserve invariants,
remove stability's obsolete global `100` ceiling, and add the non-negative
integer affinity invariant to every v3 construction, copy, codec, service,
ritual, settlement, advancement, and snapshot path. Stage-specific stability
caps remain registry-owned gameplay rules rather than profile schema limits.

## ADDED Requirements

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

## MODIFIED Requirements

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
