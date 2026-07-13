## MODIFIED Requirements

### Requirement: Realm definitions expose only foundation metadata
`RealmDefinition` SHALL contain a non-empty translation key, a non-negative
sort order, a non-empty ordered stage list, an optional next-realm
`ResourceLocation`, and the positive maximum-lifespan field introduced by the
lifespan change. Each `RealmStageDefinition` SHALL contain a stable stage
`ResourceLocation`, a non-empty translation key, a non-negative stage order,
and an optional positive `cultivation_cap`. Realm data SHALL NOT contain gain
rates, technique execution, combat logic, random success, transition targets,
breakthrough duration, or advancement stability requirements/costs in this
change.

#### Scenario: A realm definition round-trips with stage caps
- **WHEN** a valid realm with capped and uncapped stages is encoded and decoded
- **THEN** its foundation, lifespan, ordering, stage records, optional caps, and next-realm id SHALL be preserved

#### Scenario: A stage cap is invalid
- **WHEN** `cultivation_cap` is zero, negative, non-integral, or outside the supported integer range
- **THEN** definition decoding or shipped-data validation SHALL reject it with the realm and stage id

#### Scenario: A realm contains duplicate stage ids or orders
- **WHEN** validation examines a realm whose stage list repeats a stage id or stage order
- **THEN** the definition SHALL be rejected

#### Scenario: A stage is checked against a realm
- **WHEN** a service validates a requested realm-stage pair
- **THEN** it SHALL accept the pair only when the selected realm contains that exact stage id

## ADDED Requirements

### Requirement: Shipped initial stages declare exact cultivation caps
The shipped stages SHALL declare exact cultivation caps:
`myvillage:mortal_qi_sensed`, `myvillage:qi_refining_1`,
`myvillage:qi_refining_2`, and `myvillage:qi_refining_3` SHALL declare
caps `300`, `500`, `800`, and `1200` respectively. Unawakened mortal, Qi
Refining IV through IX, and Foundation Early SHALL omit the cap and SHALL be
non-cultivatable in this release.

#### Scenario: Initial stage data is validated
- **WHEN** shipped realm definitions are loaded
- **THEN** the four supported stages SHALL expose the exact ordered cap sequence `300/500/800/1200`
- **AND** every later stage SHALL remain uncapped and unavailable for gain

#### Scenario: A datapack reload changes a cap
- **WHEN** settlement next resolves the player's current stage after a successful registry reload
- **THEN** it SHALL use the current registered cap rather than a cached Java constant
