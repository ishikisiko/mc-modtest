## ADDED Requirements

### Requirement: Crafting state shares the owning cultivation snapshot
The existing clientbound cultivation snapshot SHALL include immutable crafting aptitudes and server-derived quality ceilings. The client SHALL reuse the existing latest-snapshot cache and clear crafting data on disconnect. The protocol SHALL be incremented when the wire format changes. Client input SHALL carry no crafting value, roll or chosen ceiling.

#### Scenario: Server updates an aptitude
- **WHEN** an authorized service operation updates one aptitude
- **THEN** the owning client SHALL receive both actual values and the server-derived quality caps in the existing snapshot
