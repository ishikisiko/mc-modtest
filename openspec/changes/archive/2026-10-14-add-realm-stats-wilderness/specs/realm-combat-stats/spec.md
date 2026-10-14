## ADDED Requirements

### Requirement: Realm growth is explicit and bounded
The server SHALL derive health and attack scaling from data using exponential cultivated major-realm growth and linear minor-stage growth. Mortal baseline and player stage aliases SHALL be explicit. Rules MUST reject nonfinite numbers, invalid stages, boundary reversals and shipped values outside native attribute limits.

#### Scenario: Linear steps and exponential majors
- **WHEN** stages within one realm or equal-index stages in successive cultivated realms are evaluated
- **THEN** intra-realm differences are constant and successive realm values have the configured ratio

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
