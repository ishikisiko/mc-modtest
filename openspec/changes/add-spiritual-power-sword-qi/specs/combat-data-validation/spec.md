## ADDED Requirements

### Requirement: Skill geometry and input authority are validated
Combat validation SHALL reject invalid charge bounds, costs, timing, wave geometry, family references and target limits; it SHALL verify charge, release and charge-cancel serverbound payloads remain empty and existing melee baselines remain pinned.

#### Scenario: A client-authoritative field is introduced
- **WHEN** a charge, release or charge-cancel payload declares duration, cost, position or damage
- **THEN** the authority validation SHALL fail
