## MODIFIED Requirements

### Requirement: Realm definitions expose only foundation metadata
`RealmDefinition` SHALL retain its translation, ordering, stages, optional
next-realm, and positive maximum-lifespan fields. Each `RealmStageDefinition`
SHALL retain id, translation, order, and optional positive cultivation cap and
MAY add one optional `advancement` record containing explicit target realm and
stage ids, stable-string kind, positive duration ticks, required stability,
stability cost, and interruption stability loss. Definitions SHALL contain no
gain rates, random chance, executable behavior, combat logic, pills, facility,
environment, or tribulation effects.

#### Scenario: A realm definition round-trips with advancement
- **WHEN** a valid stage has a complete advancement record
- **THEN** every target, kind, duration, requirement, cost, loss, cap, and foundation field SHALL round-trip exactly

#### Scenario: A stage omits advancement
- **WHEN** an otherwise valid stage has no advancement record
- **THEN** it SHALL remain loadable and expose no transition in this release

#### Scenario: A stage is checked against a realm
- **WHEN** a service validates a requested realm-stage pair
- **THEN** it SHALL accept the pair only when the selected realm contains the exact stage id

## ADDED Requirements

### Requirement: Advancement records are bounded and reference-safe
An advancement record SHALL use kind `ordinary` or `bottleneck`, positive
`duration_ticks`, stability values from `0` through `100`, non-negative
interruption loss, and `stability_cost <= required_stability`. Its target realm
and target stage SHALL resolve as one exact pair through current
`RegistryAccess`; a source SHALL not target itself. Unknown kinds, invalid
bounds, missing targets, and mismatched realm/stage targets SHALL fail loading
or deterministic validation with source context.

#### Scenario: A cross-realm target resolves
- **WHEN** mortal qi-sensed targets Qi Refining I in the Qi Refining realm
- **THEN** validation SHALL accept the exact registered realm-stage pair

#### Scenario: A target stage belongs to another realm
- **WHEN** the declared target realm does not contain the declared target stage
- **THEN** validation SHALL reject the source rule rather than infer a different realm

#### Scenario: A major kind appears early
- **WHEN** this release decodes an advancement kind other than `ordinary` or `bottleneck`
- **THEN** it SHALL reject the value as unsupported rather than silently treating it as ordinary

### Requirement: Shipped advancement data defines exactly four transitions
Shipped data SHALL declare the sequence and rules exactly as follows:
qi-sensed to Qi I `ordinary/100/10/5/0`, Qi I to II
`ordinary/100/20/10/0`, Qi II to III `ordinary/120/30/15/0`, and Qi III to IV
`bottleneck/200/80/30/5`, where numeric fields are duration, required
stability, success cost, and interruption loss. Qi IV and later stages SHALL
omit cap and advancement.

#### Scenario: Shipped sequence is validated
- **WHEN** deterministic cultivation data validation runs
- **THEN** it SHALL find exactly those four ordered source-target transitions and numeric rules
- **AND** it SHALL find no rule or cap at Qi IV or later
