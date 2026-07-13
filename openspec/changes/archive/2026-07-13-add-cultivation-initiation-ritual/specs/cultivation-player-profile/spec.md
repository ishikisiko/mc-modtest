## ADDED Requirements

### Requirement: Initiation reuses the immutable v1 profile without redundant state
Spiritual-root awakening and basic-technique inheritance SHALL use only the existing version-1 fields. Awakening SHALL remain derived from `spiritualRoot.isPresent()`, learned state SHALL remain derived from `learnedTechniques.containsKey(techniqueId)`, and the profile SHALL remain at schema version `1`. The implementation SHALL NOT add an awakened flag, root type/quality/tier/rarity, awakening seed/time/reroll/generator-version field, basic-breathing-learned flag, equipment slot, or other persistent initiation field.

#### Scenario: A completed initiation profile is encoded
- **WHEN** a player has an awakened root and learned `myvillage:basic_breathing`
- **THEN** `CultivationProfile.CODEC` SHALL encode only the existing v1 fields
- **AND** schema version SHALL remain `1`

#### Scenario: Code checks ritual state
- **WHEN** code asks whether the player is awakened or knows basic breathing
- **THEN** it SHALL inspect the optional root or learned-technique map respectively
- **AND** it SHALL NOT consult a redundant boolean, enum, quality, or marker field

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
