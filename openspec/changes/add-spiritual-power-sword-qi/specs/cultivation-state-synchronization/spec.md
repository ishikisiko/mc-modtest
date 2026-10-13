## ADDED Requirements

### Requirement: Power and sword-qi presentation consume server state
The power HUD SHALL derive its current value from the owning-client cultivation snapshot and its cap from synchronized stage definitions. Charge and advancing-wave presentation SHALL consume bounded server-authored state; clients SHALL NOT author resource balances, cast duration, hit positions, or damage. Disconnect and world changes SHALL clear transient presentation.

#### Scenario: Power changes after a cast
- **WHEN** the server accepts a cast and publishes the changed profile
- **THEN** the HUD SHALL display the server balance without committing a predicted balance
