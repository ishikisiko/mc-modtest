## REMOVED Requirements

### Requirement: The cultivation profile has a stable immutable v1 schema
**Reason**: The first playable loop requires durable lifespan and meditation-reserve counters, so v1 is no longer the current write shape.

**Migration**: Retain the exact v1 decoder, migrate valid v1 values to v2 with both new counters set to zero, and encode only v2 after migration.

### Requirement: Initiation reuses the immutable v1 profile without redundant state
**Reason**: Initiation must now preserve the current v2 profile rather than require schema version 1.

**Migration**: Keep awakening/root and learned-technique derivation unchanged and preserve both new v2 counters across initiation replacements.

## ADDED Requirements

### Requirement: The cultivation profile has a stable immutable v2 schema
The current immutable `CultivationProfile` SHALL contain every v1 field plus
non-negative `long` fields `lifespanConsumedTicks` and
`meditationQiReserve`. Current schema version SHALL be `2`. A new profile SHALL
use the previous default realm/stage/root/technique values and zero for every
numeric field including the two new counters. It SHALL not persist a redundant
exhausted, remaining-lifespan, calendar, awakened, or technique-equipment field.

#### Scenario: A new v2 profile is created
- **WHEN** a player has no stored cultivation attachment
- **THEN** the default SHALL be schema `2`, mortal/unawakened, with no root or techniques and all numeric values zero

#### Scenario: A valid v2 profile round-trips
- **WHEN** the current codec encodes and decodes a non-default profile
- **THEN** every old field plus lifespan and reserve SHALL equal the original

### Requirement: V1 profiles migrate explicitly and losslessly to v2
The codec SHALL retain a version-specific v1 decoder and a version-specific v2
decoder. A valid v1 value SHALL migrate through a pure validated transformation
that preserves every old field and unknown syntactically valid id exactly and
sets both new counters to zero. The current encoder SHALL write only version 2;
versions other than 1 and 2 SHALL fail explicitly.

#### Scenario: A non-default v1 profile is loaded
- **WHEN** persisted v1 data contains progress, stability, power, root affinities, techniques, mastery, and unknown ids
- **THEN** the in-memory v2 profile SHALL preserve all of them exactly
- **AND** lifespan and reserve SHALL both equal zero

#### Scenario: Legacy progress exceeds a later gameplay cap
- **WHEN** a valid v1 profile carries progress above a current stage cap
- **THEN** migration SHALL preserve that raw progress rather than clamp or reinterpret it

#### Scenario: An unsupported version is loaded
- **WHEN** persisted data declares a schema version other than 1 or 2
- **THEN** decoding SHALL fail with a controlled unsupported-version error rather than reset

### Requirement: V2 counter invariants apply at every construction boundary
`lifespanConsumedTicks` and `meditationQiReserve` SHALL be non-negative. Profile
constructors, copy helpers, codecs, service mutations, reset, snapshot codecs,
and initiation replacements SHALL preserve the same invariant.

#### Scenario: A negative v2 counter is supplied
- **WHEN** construction or decoding supplies a negative lifespan or reserve
- **THEN** it SHALL fail without installing or synchronizing a replacement

#### Scenario: An existing profile is awakened or initiated
- **WHEN** either ritual succeeds on a v2 profile with nonzero lifespan or reserve
- **THEN** the final root/stage or technique mutation SHALL preserve both counters exactly

## MODIFIED Requirements

### Requirement: Profile schema changes use explicit migrations
The retained v1 decoder SHALL accept schema version `1`, the current v2 decoder
SHALL accept schema version `2`, and both SHALL reject any other version with a
controlled codec error. Future schema-changing implementations MUST retain all
supported old decoders and MUST migrate decoded old values through explicit,
validated `vN` to `vN+1` transformations before writing the current schema.

#### Scenario: An unsupported schema is loaded by v2 code
- **WHEN** a persisted profile declares a schema version other than `1` or `2`
- **THEN** the codec SHALL fail explicitly rather than guess fields or silently reset the profile

#### Scenario: A future schema is introduced
- **WHEN** a later change adds another persisted field
- **THEN** that change MUST add migration coverage from v2 and retain the v1-to-v2 path
- **AND** it MUST NOT reinterpret or mutate existing v1/v2 field meanings
