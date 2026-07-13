# cultivation-gain Specification

## Purpose
TBD - created by archiving change add-basic-breathing-cultivation-gain. Update Purpose after archive.
## Requirements
### Requirement: Stage caps prevent normal gameplay overflow
Normal and spirit settlements SHALL resolve optional positive
`cultivation_cap` from the current registered source stage, apply at most the
remaining progress, and never carry excess into another stage. A missing cap
SHALL make that stage non-cultivatable and non-chargeable. Existing over-cap data
SHALL be preserved but receive no further progress.

#### Scenario: Progress reaches a cap mid-settlement
- **WHEN** calculated normal or spirit gain exceeds the exact remaining stage capacity
- **THEN** stored progress SHALL equal the cap and no overflow SHALL be banked

#### Scenario: An over-cap migrated or debug profile meditates
- **WHEN** stored progress already exceeds the current cap
- **THEN** raw progress SHALL remain unchanged and no progress or stone cost SHALL be applied

#### Scenario: A stage has no cap in this release
- **WHEN** a player at Qi IV or later attempts cultivation gain
- **THEN** settlement SHALL reject the stage without gain, item cost, or synthesized future cap

### Requirement: Stability and mastery remain one-times rate at the progress cap
Stability SHALL increase toward its existing maximum 100 and Basic Breathing
mastery SHALL increase at 10/year in both modes even when progress is capped.
Spirit mode SHALL not multiply either value.

#### Scenario: A capped player stabilizes normally
- **WHEN** progress is capped and stability or mastery can still increase
- **THEN** applicable stability/mastery SHALL accrue at the ordinary rate

#### Scenario: Stability is already 100
- **WHEN** another stability point becomes due
- **THEN** stability SHALL remain 100 without blocking applicable mastery

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
- **AND** eligible stability SHALL use current affinity without a stone cost
- **AND** legacy reserve SHALL remain unchanged

### Requirement: Missing acceleration downgrades to free ordinary meditation
When a due non-capped spirit batch lacks its complete cost, the server SHALL
remove no stone, transition the existing session to
normal meditation, and apply the normal affinity-based result for that due
ten-tick interval when otherwise valid. It SHALL retain mastery eligibility,
apply no stability while progress began below cap, and send one clear
transition-only downgrade status. It SHALL not
stop free practice, use partial payment, spend reserve, or consume current
spiritual power.

#### Scenario: Qi III has only two stones
- **WHEN** a due Qi-III spirit batch requires three stones but ordinary inventory contains two
- **THEN** zero stones SHALL be consumed and the active session SHALL become normal
- **AND** the due settlement SHALL use current affinity rather than fixed spirit gain

#### Scenario: Stones remain in an external container
- **WHEN** ordinary inventory cannot fund the full cost but an Ender Chest or placed container can
- **THEN** the server SHALL ignore the external stones and downgrade to normal

### Requirement: Cultivation gain comes only from declared meditation
This change SHALL not award generic cultivation progress for combat, mining,
exploration, crafting, movement, idle online time, or item use outside an active
eligible meditation settlement. It SHALL not recover spiritual power, apply
element modifiers, choose a primary technique, or advance stages automatically.

#### Scenario: A player defeats an entity or mines ore
- **WHEN** no active eligible meditation settlement occurs
- **THEN** those actions SHALL grant no cultivation progress, stability, or Basic Breathing mastery

### Requirement: Normal meditation gains current spiritual affinity every ten eligible ticks
An active eligible server-owned normal session SHALL require registered learned
Basic Breathing to produce normal progress. Every ten eligible active ticks,
it SHALL add exactly the current server-profile `spiritualAffinity`, subject only
to current-stage cap clamping and overflow-safe arithmetic. The client SHALL NOT
supply or override affinity, cadence, cap, or result.

#### Scenario: Default affinity settles normally
- **WHEN** an eligible uncapped profile with affinity `10` completes ten active normal-meditation ticks
- **THEN** cultivation progress SHALL increase by exactly `10`

#### Scenario: A non-default affinity settles normally
- **WHEN** an eligible profile with affinity `37` has at least 37 remaining capacity and completes ten active normal ticks
- **THEN** cultivation progress SHALL increase by exactly `37`

#### Scenario: Normal gain reaches the cap
- **WHEN** current affinity exceeds the remaining stage capacity
- **THEN** progress SHALL equal the cap and no overflow SHALL be stored or carried into another stage

#### Scenario: Affinity is zero
- **WHEN** a valid profile with affinity `0` completes a normal settlement
- **THEN** no progress or stability SHALL be added while any separately due mastery SHALL still commit

### Requirement: Spirit meditation gains fifty progress for a complete layer-priced batch
An active eligible server-owned spirit session SHALL require registered learned
Basic Breathing to produce spirit progress. Every ten eligible active ticks,
the server SHALL derive a complete cost from the authoritative source stage's
optional positive `spirit_stone_cost` definition field and,
when funded, remove that many `myvillage:low_grade_spirit_stone` items and add
exactly `50` total progress when at least 50 capacity remains. Qi-sensed mortal
and Qi I SHALL cost `1`, Qi II SHALL cost `2`, and Qi III SHALL cost `3`. A
nonempty final batch with less than 50 remaining capacity SHALL pay the same
complete cost and clamp output to the exact cap. Affinity SHALL not alter spirit
gain or cost.

#### Scenario: Qi II completes a funded spirit batch
- **WHEN** an eligible Qi-II profile has at least 50 remaining capacity and at least two ordinary-inventory stones
- **THEN** exactly two stones SHALL be consumed and progress SHALL increase by exactly `50`

#### Scenario: Qi-sensed initiation remains usable
- **WHEN** an eligible qi-sensed mortal completes a funded spirit batch
- **THEN** exactly one stone SHALL be consumed for the same fixed `50` progress

#### Scenario: A funded final batch has thirty capacity
- **WHEN** an eligible Qi-III profile is 30 points below its cap and holds at least three stones
- **THEN** exactly three stones SHALL be consumed and progress SHALL increase by exactly `30` to the cap

#### Scenario: Affinity changes during spirit mode
- **WHEN** the authoritative profile affinity differs from `10`
- **THEN** a funded spirit batch SHALL still produce the fixed total `50` before cap clamping

#### Scenario: A cultivatable stage omits its spirit cost
- **WHEN** a registered source stage has a cultivation cap but no positive `spirit_stone_cost`
- **THEN** definition validation SHALL reject the inconsistent stage rather than infer a cost from its id

### Requirement: Stability consolidates only after full progress at the affinity rate
Every cultivatable source stage SHALL derive a stability cap as integer-floor
`cultivation_cap / 2`, and its advancement stability requirement SHALL equal
that cap. A settlement that begins below the progress cap SHALL add no
stability, including when that settlement reaches the cap. Beginning with the
next ten-eligible-tick settlement at or above full progress, both normal and
spirit meditation SHALL add exactly current `spiritualAffinity` stability,
clamped to the derived cap. This stability work SHALL consume no spirit stone
and SHALL not use the fixed spirit progress result. Basic Breathing mastery
alone SHALL continue at exactly `10` points per configured cultivation year
with one transient fixed-point remainder in both modes and at either side of
the progress cap.

#### Scenario: Progress is not yet full
- **WHEN** a settlement begins one point below the current progress cap
- **THEN** stability SHALL remain unchanged even if that settlement fills the progress cap

#### Scenario: The next normal batch consolidates stability
- **WHEN** a full-progress profile with affinity `10` and stability below its derived cap completes ten normal ticks
- **THEN** stability SHALL increase by exactly `10` without changing progress

#### Scenario: A full-progress spirit session consolidates stability
- **WHEN** a full-progress profile with affinity `37` completes ten spirit ticks
- **THEN** stability SHALL increase by exactly `37` subject to its derived cap
- **AND** no spirit stone SHALL be scanned or consumed

#### Scenario: Stability reaches its stage cap
- **WHEN** the affinity result exceeds remaining stability capacity
- **THEN** stability SHALL equal integer-floor half of the stage progress cap and no overflow SHALL be banked

#### Scenario: One configured year is practiced across both phases
- **WHEN** eligible meditation accumulates one configured cultivation year before and after progress becomes full
- **THEN** Basic Breathing mastery SHALL gain exactly `10` independent of affinity, mode, progress, or stability
