## MODIFIED Requirements

### Requirement: Bounded synchronized selection
The server SHALL offer six unique eligible candidates, allow one lock, require three selections within the configurable point budget, and enforce exclusive-group limits. There SHALL be no additional limit on how many selected fates are high tier. Client messages SHALL contain only bounded intent, slot or selection mask, and seen revision. The server SHALL reject stale, malformed, throttled and already-confirmed mutations.

#### Scenario: Low budget with expensive lock
- **WHEN** a player locks an expensive candidate and the server budget is reduced
- **THEN** reroll preserves that candidate but supplies a legal low-cost triple, and confirmation rechecks the current server budget

#### Scenario: Forged confirmation
- **WHEN** a client submits an old revision or a mask outside the six current slots
- **THEN** the server rejects the request without changing state or granting any effect

#### Scenario: Multiple high-tier fates fit the budget
- **WHEN** a current three-fate selection contains multiple high-tier definitions in distinct groups and its total cost fits the server budget
- **THEN** both client eligibility and server confirmation SHALL accept it without a rarity-count rejection
- **AND** insufficient budget, duplicate groups, stale state and invalid sessions SHALL still be rejected
