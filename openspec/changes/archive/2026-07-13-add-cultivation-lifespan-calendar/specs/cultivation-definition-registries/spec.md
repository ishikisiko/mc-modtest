## ADDED Requirements

### Requirement: Realm definitions declare maximum lifespan
Every `RealmDefinition` SHALL contain a positive integer
`maximum_lifespan_years`. Runtime lifespan calculations SHALL resolve the field
through current `RegistryAccess` and SHALL not infer it from realm id, sort
order, a Java table, or the player profile. The shipped mortal, qi-refining, and
foundation-establishment values SHALL be `80`, `120`, and `240` respectively.

#### Scenario: Shipped realm data is validated
- **WHEN** the three shipped realm definitions load
- **THEN** each SHALL expose its exact positive maximum lifespan

#### Scenario: A datapack omits or invalidates the field
- **WHEN** a realm omits `maximum_lifespan_years` or supplies zero, a negative value, or a non-integer
- **THEN** definition loading or reference validation SHALL fail with the realm id and field

#### Scenario: A current realm is resolved
- **WHEN** lifespan status is calculated for a profile
- **THEN** the service SHALL use the maximum from the current registered realm definition
