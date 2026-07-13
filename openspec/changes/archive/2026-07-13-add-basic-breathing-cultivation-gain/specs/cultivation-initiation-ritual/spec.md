## MODIFIED Requirements

### Requirement: Initiation does not implement cultivation execution or progression
The awakening and inheritance ritual actions SHALL NOT themselves execute Basic
Breathing, add progress, stability, mastery, reserve, spiritual power, or
automatically advance a realm/stage. Learning the technique SHALL remain
acquisition-only. After both rituals are complete, a separately requested and
server-approved meditation session MAY execute the fixed Basic Breathing gain
defined by `cultivation-gain`. This change SHALL NOT add a generic technique
executor, technique equipment, combat effects, element bonuses, root quality,
rerolls, automatic advancement, or breakthrough behavior.

#### Scenario: A player completes both steles
- **WHEN** awakening and inheritance succeed
- **THEN** the player SHALL remain at `myvillage:mortal_qi_sensed` with unchanged progress, stability, spiritual power, and zero newly granted mastery
- **AND** neither stele SHALL start a meditation session or consume a spirit stone

#### Scenario: The initiated player later meditates
- **WHEN** the player separately sends an eligible meditation start intent and an active settlement becomes due
- **THEN** gain MAY be produced by the meditation service rather than by either ritual
- **AND** `myvillage:basic_breathing` SHALL still have no generic executor field
