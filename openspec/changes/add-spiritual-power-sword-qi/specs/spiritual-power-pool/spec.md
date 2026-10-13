## ADDED Requirements

### Requirement: Stage data bounds gameplay spiritual power
Gameplay spiritual power SHALL use the existing persistent `currentSpiritualPower` field and the current realm stage's non-negative data-defined cap. Service mutations and login normalization SHALL keep gameplay power between zero and that cap. Missing stage capacity SHALL provide zero usable power without inventing a cap. The legacy meditation reserve SHALL remain separate.

#### Scenario: Existing power exceeds a newly applicable cap
- **WHEN** a player logs in with stored power greater than the resolved stage cap
- **THEN** the service SHALL clamp power to that cap and synchronize the owning client
- **AND** it SHALL preserve unrelated profile fields

### Requirement: Spending is atomic and regeneration is server owned
Only `CultivationService` SHALL install changed power. A non-negative spend SHALL either deduct the complete cost once or leave the profile unchanged. Regeneration interval, base gain, meditation factor and post-spend delay SHALL come from data; gain SHALL use the running core-technique factor and never exceed the stage cap. Rejected spends SHALL NOT defer regeneration. No offline time SHALL produce gain.

#### Scenario: Insufficient power rejects a cast
- **WHEN** the requested cost exceeds available power
- **THEN** spending SHALL fail without partial deduction or a changed profile snapshot

#### Scenario: Recovery resumes after a successful spend
- **WHEN** the data-defined delay after spending has elapsed and the player is eligible for recovery
- **THEN** the next due server settlement SHALL restore the data-derived amount up to the cap

### Requirement: Melee and movement remain independently usable
Existing melee attacks and dodge SHALL NOT spend spiritual power or acquire a new power prerequisite in this slice.

#### Scenario: A learned dodge is used at zero power
- **WHEN** all existing dodge eligibility checks pass and power is zero
- **THEN** dodge SHALL remain usable with its existing timing and cooldown rules
