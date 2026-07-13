## ADDED Requirements

### Requirement: Cultivation settlements synchronize only final changed profiles
Each successful 100-tick settlement that installs a changed profile SHALL send
the owning client exactly one final profile snapshot through the existing
clientbound path. Transient tick counters and fixed-point remainders SHALL not
be serialized or synchronized. A batch with no whole profile change, a rejected
batch, and ordinary meditation ticks between batches SHALL send no profile
snapshot.

#### Scenario: One batch changes several profile fields
- **WHEN** progress, stability, mastery, and reserve change in one settlement
- **THEN** the owning client SHALL receive one snapshot containing all final values
- **AND** no intermediate reserve-credit or progress-only snapshot SHALL be sent

#### Scenario: No whole result is due
- **WHEN** a 100-tick settlement updates only transient fractional remainders
- **THEN** no profile snapshot SHALL be sent

### Requirement: Acceleration downgrade is a bounded runtime status
When spirit acceleration becomes unavailable, the server SHALL send one
clientbound status describing the transition to ordinary meditation. It SHALL
not resend that status every tick or expose an inventory/profile mutation path
to the client.

#### Scenario: Spirit resources run dry
- **WHEN** the authoritative session changes from spirit to normal mode
- **THEN** the owning client SHALL receive one downgrade transition
- **AND** the session SHALL continue without a changed-profile snapshot unless settlement also changed the profile

### Requirement: The read-only H screen presents current-stage gain status
The existing H profile screen SHALL show current-stage progress and its
currently resolved cap, stability, Basic Breathing mastery, and persistent
meditation reserve from read-only client state. An uncapped or unavailable
stage SHALL display a clear unavailable/release-limit state. Rendering the
screen SHALL send no cultivation intent or profile mutation.

#### Scenario: A supported stage is displayed
- **WHEN** a current snapshot and registered stage cap are available
- **THEN** H SHALL show current progress against that cap and the synchronized stability, mastery, and reserve values

#### Scenario: A later stage has no cap
- **WHEN** the profile references Qi Refining IV or a stage whose definition is unavailable
- **THEN** H SHALL show cultivation gain as unavailable rather than invent a cap
