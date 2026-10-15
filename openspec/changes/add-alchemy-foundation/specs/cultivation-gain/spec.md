## MODIFIED Requirements

### Requirement: Stability and mastery remain one-times rate at the progress cap
Stability SHALL consolidate only after full progress, at current spiritual affinity per eligible ten-tick settlement, scaled only by an active server-owned Ningshen medicine multiplier and rounded down, up to integer-floor half the source cultivation cap. With no active medicine the multiplier SHALL be one. Basic Breathing mastery SHALL remain on the independent `10/year` clock in both modes even when progress is capped. Spirit mode SHALL multiply neither stability nor mastery, and SHALL charge no stone for stability work.

#### Scenario: A capped player stabilizes normally
- **WHEN** progress is capped, no stability medicine is active, and stability or mastery can still increase
- **THEN** applicable stability SHALL accrue at current affinity up to its derived cap and mastery SHALL accrue on its unchanged configured-year clock

#### Scenario: Stability is already at its derived cap
- **WHEN** another stability point becomes due
- **THEN** stability SHALL remain at its stage-derived cap without blocking applicable mastery

### Requirement: Stone conversion is server-authoritative and logically atomic
For each due non-capped spirit batch, the server SHALL derive the cost from the
current source stage, inspect only ordinary player inventory, validate the full
proposed profile, and remove exactly the complete cost before one
`CultivationService` commit. An incomplete removal SHALL restore every removed
stone and install no profile. A profile commit that does not install the
replacement SHALL restore the full cost and retain the old profile. Once the
attachment replacement succeeds, a later client snapshot-delivery failure
SHALL NOT restore stones or replay progress because the gameplay commit already
succeeded. Legacy reserve, external containers, and client-authored counts
SHALL not participate.

#### Scenario: Complete cost and profile commit succeed
- **WHEN** an eligible Qi-III batch removes exactly three stones and the proposed replacement installs
- **THEN** one final profile SHALL contain the clamped progress gain and any separately due mastery
- **AND** the owning client SHALL receive no intermediate removal or profile snapshot

#### Scenario: Item removal is incomplete
- **WHEN** fewer than the complete server-derived cost can be removed
- **THEN** every removed stone SHALL be restored and no spirit profile result SHALL install

#### Scenario: Profile installation fails after item removal
- **WHEN** the complete cost was removed but validation or attachment replacement fails before installation
- **THEN** the full item cost SHALL be restored and the old profile SHALL remain installed

#### Scenario: Snapshot delivery fails after installation
- **WHEN** the immutable replacement was installed but its immediate clientbound snapshot cannot be delivered
- **THEN** the server SHALL retain the committed profile and consumed cost without duplicating a rollback refund

#### Scenario: The progress cap is already reached
- **WHEN** remaining current-stage capacity is zero
- **THEN** the server SHALL not scan, remove, or restore any stone for progress
- **AND** eligible stability SHALL use current affinity and the active server-owned medicine multiplier (one when absent), rounded down and clamped to remaining capacity, without a stone cost
- **AND** legacy reserve SHALL remain unchanged

### Requirement: Stability consolidates only after full progress at the affinity rate
Every cultivatable source stage SHALL derive a stability cap as integer-floor
`cultivation_cap / 2`, and its advancement stability requirement SHALL equal
that cap. A settlement that begins below the progress cap SHALL add no
stability, including when that settlement reaches the cap. Beginning with the
next ten-eligible-tick settlement at or above full progress, both normal and
spirit meditation SHALL add `floor(current spiritualAffinity * medicineMultiplier)`
stability, clamped to the derived cap. The multiplier SHALL be one without active
Ningshen medicine and SHALL otherwise come from the server-owned remaining
medicine state and bundled alchemy rules. It SHALL affect neither progress nor
mastery, study or advancement. This stability work SHALL consume no spirit stone
and SHALL not use the fixed spirit progress result. Basic Breathing mastery
alone SHALL continue at exactly `10` points per configured cultivation year
with one transient fixed-point remainder in both modes and at either side of
the progress cap.

#### Scenario: Progress is not yet full
- **WHEN** a settlement begins one point below the current progress cap
- **THEN** stability SHALL remain unchanged even if that settlement fills the progress cap

#### Scenario: The next normal batch consolidates stability
- **WHEN** a full-progress profile without active medicine, with affinity `10` and stability below its derived cap completes ten normal ticks
- **THEN** stability SHALL increase by exactly `10` without changing progress

#### Scenario: A full-progress spirit session consolidates stability
- **WHEN** a full-progress profile without active medicine and with affinity `37` completes ten spirit ticks
- **THEN** stability SHALL increase by exactly `37` subject to its derived cap
- **AND** no spirit stone SHALL be scanned or consumed

#### Scenario: Stability reaches its stage cap
- **WHEN** the affinity result exceeds remaining stability capacity
- **THEN** stability SHALL equal integer-floor half of the stage progress cap and no overflow SHALL be banked

#### Scenario: One configured year is practiced across both phases
- **WHEN** eligible meditation accumulates one configured cultivation year before and after progress becomes full
- **THEN** Basic Breathing mastery SHALL gain exactly `10` independent of affinity, mode, progress, or stability

#### Scenario: Active stability medicine respects the remaining cap
- **WHEN** a full-progress profile with current affinity `10`, active Ningshen multiplier `1.5` and only `7` stability capacity completes an eligible batch
- **THEN** stability SHALL increase by `7`, with no overflow, progress change or stone cost
