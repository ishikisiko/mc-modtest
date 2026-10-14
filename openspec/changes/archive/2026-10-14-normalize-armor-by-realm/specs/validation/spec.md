## MODIFIED Requirements

### Requirement: Realm statistics and regional wildlife have layered verification
The repository SHALL validate numerical rules, stage aliases and region ranges, test growth, damage bucket ordering, invalid data, deterministic sampling, persistence and modifier idempotence, and provide server diagnostic commands and a bounded server evidence workflow. Armor-normalization regression SHALL cover same-realm and both directions of cross-realm damage, ordinary scale-one and mortal/unassigned behavior, invalid-assignment fallback, zero damage, large finite entering damage, unchanged armor/toughness and base reduction ceilings, bypass tags, source enchantments, Protection, Breach, potion effects, typed resistance, absorption, full/partial shields, equipment durability and invulnerability-frame excess damage. Tests SHALL distinguish actual armor pressure from the complete damage entering and leaving armor, prove each applicable layer runs once, and preserve existing melee, sword-qi, beast and ordinary vanilla damage routes. Runtime and packaging checks SHALL prove the shared armor policy is active in the practical artifact; pure arithmetic tests or static source checks alone SHALL NOT establish runtime compatibility. Evidence SHALL report phase durations and distinguish automation from owner acceptance.

#### Scenario: Automated checks pass
- **WHEN** validators, tests, build and bounded server checks pass without an owner gameplay session
- **THEN** numerical and observed runtime results are recorded while feel, density and unobserved client lifecycle items remain not_verified

#### Scenario: Scale-one compatibility is compared
- **WHEN** the same accepted hit and equipment fixture are evaluated with defender scale one and the ordinary vanilla calculation
- **THEN** armor output, subsequent mitigation and applicable durability costs SHALL match
- **AND** a malformed assignment SHALL safely produce that same fallback behavior

#### Scenario: Layered armor evidence is collected
- **WHEN** bounded developer evidence exercises applicable scaled, cross-realm, bypass and layered mitigation fixtures
- **THEN** retained traces SHALL expose actual `D_atArmor`, `S_def`, `D_pressure` and complete armor output or an explicit skipped path
- **AND** the report SHALL retain attempts, failures, test setup and actual observations separately from unobserved compatibility surfaces

#### Scenario: A thirty-armor twelve-toughness fixture is used
- **WHEN** administrators temporarily equip or configure a diagnostic defender with armor 30 and toughness 12
- **THEN** the fixture SHALL be identified as test setup for the armor formula and caps
- **AND** it SHALL NOT establish a new shipped armor set, recipe, item, innate realm armor grant or natural equipment-acquisition result

#### Scenario: A compatibility surface has only arithmetic coverage
- **WHEN** numerical tests pass but a claimed shield, enchantment, potion, equipment-durability or invulnerability interaction has not been exercised through its actual runtime path
- **THEN** evidence SHALL identify the numerical result and leave that unobserved runtime surface not_verified
- **AND** no developer fixture or capture SHALL substitute for the owner's balance, feel or physical-client verdict
