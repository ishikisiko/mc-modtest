## ADDED Requirements

### Requirement: Active Basic Breathing settles at fixed effective-year rates
Only an active server-owned meditation session SHALL produce cultivation output.
Its current profile SHALL have learned registered `myvillage:basic_breathing`. Normal
meditation SHALL yield 100 progress, 10 stability, and 10 Basic Breathing
mastery per configured cultivation year. Spirit meditation SHALL yield 400
total progress, 10 stability, and 10 mastery per year. Lifespan SHALL continue
at its independent ordinary rate in both modes.

#### Scenario: One default cultivation year is practiced normally
- **WHEN** an eligible player completes 144000 active normal-meditation ticks without caps
- **THEN** progress SHALL increase by 100, stability by 10, mastery by 10, and consumed lifespan by the ordinary 144000 ticks

#### Scenario: One default cultivation year uses spirit acceleration
- **WHEN** reserve fully funds 144000 active spirit-meditation ticks without caps
- **THEN** progress SHALL increase by 400 while stability and mastery each increase by 10
- **AND** lifespan SHALL increase by the same 144000 ticks as normal meditation

#### Scenario: Basic Breathing is missing or forgotten
- **WHEN** the current definition is unavailable or the profile no longer contains the technique
- **THEN** settlement SHALL fail closed and stop the active session without profile gain

### Requirement: Settlement uses 100-tick batches and transient fixed-point remainders
The server SHALL evaluate output every 100 active meditation ticks. Integer
fixed-point remainders SHALL carry across settlements within the session so
fractional rates are not lost every five seconds. A session end MAY discard only
remainders below one whole point per output. A settlement with no whole profile
or reserve change SHALL not call `setData` or send a profile snapshot.

#### Scenario: Several short settlement batches accumulate
- **WHEN** individual 100-tick batches each produce less than one whole point
- **THEN** their remainders SHALL combine until the correct whole output is due

#### Scenario: The session ends with fractions
- **WHEN** a session stops before a remainder reaches one point
- **THEN** no fractional value SHALL be serialized and the loss SHALL remain below one point per output

#### Scenario: The time scale reloads
- **WHEN** ticks-per-day or days-per-year changes during an active session
- **THEN** the session SHALL be interrupted before settlements use the new denominator

### Requirement: Stage caps prevent normal gameplay overflow
Normal settlement SHALL resolve optional positive `cultivation_cap` from the
current registered stage, apply at most the remaining progress, and never carry
excess into another stage. A missing cap SHALL make that stage non-cultivatable.
Existing over-cap data SHALL be preserved but receive no further progress.

#### Scenario: Progress reaches a cap mid-settlement
- **WHEN** calculated gain exceeds the exact remaining stage capacity
- **THEN** stored progress SHALL equal the cap and no overflow SHALL be banked

#### Scenario: An over-cap migrated or debug profile meditates
- **WHEN** stored progress already exceeds the current cap
- **THEN** raw progress SHALL remain unchanged and no further progress SHALL be added

#### Scenario: A stage has no cap in this release
- **WHEN** a player at Qi IV or later attempts cultivation gain
- **THEN** settlement SHALL reject the stage as not currently cultivatable

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

### Requirement: Reserve funds only applied spirit bonus progress
One reserve point SHALL fund one whole extra progress point above the normal
rate. One low-grade spirit stone SHALL add 100 reserve. The server SHALL spend
reserve only for bonus progress actually applied after stage-cap clamping and
SHALL preserve all unspent reserve across settlement, interruption, logout,
death, and later sessions.

#### Scenario: Reserve funds a bonus point
- **WHEN** one applicable extra progress point is due and reserve is positive
- **THEN** progress SHALL gain that bonus and reserve SHALL decrease by one

#### Scenario: The cap removes all bonus gain
- **WHEN** no extra progress can be applied because progress is capped
- **THEN** no reserve SHALL be spent and no stone SHALL be scanned or consumed

#### Scenario: Meditation is interrupted after stone conversion
- **WHEN** a stone has credited reserve and the session later stops
- **THEN** all unspent credited reserve SHALL remain in the persistent profile

### Requirement: Stone conversion is server-authoritative and logically atomic
The server SHALL inspect only the player's ordinary inventory when a whole bonus
point is due and reserve is insufficient. It SHALL validate one complete
settlement before consuming a stone. Failed item removal SHALL install no
profile replacement; failed profile commit after removal SHALL restore the
stone. Success SHALL install one final profile containing gain, reserve credit,
and reserve spend and SHALL send one snapshot.

#### Scenario: A stone is available
- **WHEN** reserve is insufficient and the inventory contains a low-grade spirit stone
- **THEN** exactly one stone SHALL be removed and 100 reserve SHALL be credited before applicable bonus spend in the same logical transaction

#### Scenario: Profile validation fails after removal
- **WHEN** a proposed stone-backed settlement cannot commit
- **THEN** the consumed stone SHALL be restored and the old profile SHALL remain installed

#### Scenario: An external container holds the only stone
- **WHEN** no stone exists in ordinary player inventory
- **THEN** settlement SHALL not scan or consume from Ender Chest, placed containers, or external inventories

### Requirement: Missing acceleration downgrades to free ordinary meditation
The server SHALL downgrade spirit meditation when acceleration is unavailable.
When a whole spirit bonus point is due but neither reserve nor an inventory stone
can fund it, the server SHALL transition the existing active session to normal
meditation, retain base progress/stability/mastery eligibility, and send one
clear downgrade status. It SHALL not stop ordinary practice or consume current
spiritual power.

#### Scenario: Acceleration runs dry
- **WHEN** reserve is zero and no low-grade spirit stone is available
- **THEN** the session SHALL become normal meditation and continue free base cultivation

### Requirement: Cultivation gain comes only from declared meditation
This change SHALL not award generic cultivation progress for combat, mining,
exploration, crafting, movement, idle online time, or item use outside an active
eligible meditation settlement. It SHALL not recover spiritual power, apply
element modifiers, choose a primary technique, or advance stages automatically.

#### Scenario: A player defeats an entity or mines ore
- **WHEN** no active eligible meditation settlement occurs
- **THEN** those actions SHALL grant no cultivation progress, stability, or Basic Breathing mastery
