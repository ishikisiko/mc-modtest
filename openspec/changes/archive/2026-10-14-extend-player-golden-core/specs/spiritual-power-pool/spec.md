## MODIFIED Requirements

### Requirement: Stage data bounds gameplay spiritual power
Gameplay spiritual power SHALL use the existing persistent `currentSpiritualPower` field and the current realm stage's non-negative data-defined cap. Service mutations and login normalization SHALL keep gameplay power between zero and that cap. An advancement SHALL immediately resolve the new stage capacity through the existing service and synchronized registries. Increasing the capacity SHALL preserve the prior current power without refilling it; any lower target cap SHALL use the ordinary clamp. Golden Core late SHALL retain its authored power capacity despite having no progress cap or transition. Missing stage capacity SHALL provide zero usable power without inventing a cap. The legacy meditation reserve SHALL remain separate.

#### Scenario: Existing power exceeds a newly applicable cap
- **WHEN** a player logs in with stored power greater than the resolved stage cap
- **THEN** the service SHALL clamp power to that cap and synchronize the owning client
- **AND** it SHALL preserve unrelated profile fields

#### Scenario: A Foundation player reaches Golden Core
- **WHEN** the committed advancement increases the stage's spiritual-power capacity
- **THEN** the owning client SHALL display the new capacity while current power remains unchanged until ordinary server-owned regeneration
- **AND** advancement SHALL neither reset the regeneration clock to grant an immediate bonus nor create another power field
