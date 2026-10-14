# realm-combat-stats Specification

## Purpose
Define the server-owned realm growth, attribute composition and damage diagnostics shared by physical combatants.
## Requirements
### Requirement: Realm growth is explicit and bounded
The server SHALL derive health and attack scaling from data using exponential cultivated major-realm growth and linear minor-stage growth. Mortal baseline and player stage aliases SHALL be explicit. Player aliases SHALL cover the expanded playable registry through Foundation late and Golden Core late, retaining all existing mappings; the shared numerical ladder and its growth coefficients SHALL remain unchanged. A stage alias SHALL match its owning registered realm and zero-based minor-stage order. Rules MUST reject nonfinite numbers, invalid stages, boundary reversals and shipped values outside native attribute limits.

#### Scenario: Linear steps and exponential majors
- **WHEN** stages within one realm or equal-index stages in successive cultivated realms are evaluated
- **THEN** intra-realm differences are constant and successive realm values have the configured ratio

#### Scenario: Golden Core late is reached normally
- **WHEN** the server commits the final player transition to myvillage:golden_late
- **THEN** numerical statistics SHALL resolve it to the existing Golden Core major realm and minor index 2
- **AND** attribute refresh SHALL preserve current wounds and stable modifier identities

### Requirement: Attribute refresh is idempotent
Derived server modifiers SHALL have stable identities and SHALL refresh after profile changes and player lifecycle transitions. Refresh MUST NOT heal or compound prior modifiers.

#### Scenario: Repeated refresh
- **WHEN** a wounded player is refreshed twice at the same realm
- **THEN** effective attributes and current health equal the result after one refresh

### Requirement: Damage stages are shared and inspectable
Melee, sword qi and beast attacks SHALL use one numerical damage policy with explicit base, flat, move, additive-bonus and total factors. Vanilla armor and enchantments SHALL apply once; additional typed resistance SHALL apply once. Diagnostics SHALL separate requested damage, mitigation and health loss.

#### Scenario: Neutral modifiers
- **WHEN** bonus and resistance are zero and total multiplier is one
- **THEN** the ordinary move and vanilla mitigation results are preserved

#### Scenario: Invalid resistance
- **WHEN** configuration contains a nonfinite or unsupported resistance
- **THEN** validation fails naming the invalid field
